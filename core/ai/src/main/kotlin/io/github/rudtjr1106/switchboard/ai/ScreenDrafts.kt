package io.github.rudtjr1106.switchboard.ai

import io.github.rudtjr1106.switchboard.config.ScreenCatalog
import io.github.rudtjr1106.switchboard.config.ScreenInfo

/**
 * 모델 없이 화면 라벨·구분 초안을 만든다 (온디바이스 AI 하네스의 결정적 단계)
 *
 * 작은 모델은 `AdminStudyGroupSchedule` 같은 이름을 자주 틀리게 옮기므로, 먼저 여기서 확실한 초안을 만들고
 * 모델은 초안을 다듬기만 하게 한다. 모델이 없거나 답이 이상하면 이 초안이 그대로 쓰인다.
 *
 * - 라벨: 선언 위 주석이 짧은 한국어 이름이면 그것, 아니면 CamelCase 토큰을 [glossary] 로 옮긴 것.
 *   둘 다 있으면 서로 겹치는 단어가 있을 때 주석을(더 구체적이다), 겹치지 않으면 토큰 번역을 믿는다.
 * - 구분: 구역 제목(`/**공지 섹션**/`, `// region 인증`)이 있으면 그것, 없으면 로그인·가입류 토큰은 '시작·인증',
 *   나머지는 첫 토큰의 번역.
 */
object ScreenDrafts {

    const val AUTH_GROUP = "시작·인증"
    const val MY_GROUP = "MY"

    fun drafts(hints: List<ScreenHint>): List<ScreenInfo> =
        listOf(LlmScreenLabeler.ALL_SCREEN) + hints.filter { it.id != ScreenCatalog.ALL }.distinctBy { it.id }.map(::draft)

    /** 초안의 라벨을 어디서 얻었는지. 모델에게 맡길지 정하는 기준이 된다 */
    enum class Source {
        /** 소스 주석(사람이 쓴 이름)이 id 와 맞아서 그대로 썼다. 모델이 고치지 않는다 */
        COMMENT,

        /** id 의 모든 토큰을 용어 사전으로 옮겼다 */
        GLOSSARY,

        /** 사전에 없는 토큰이 있어 영어가 섞였다. 모델이 고치는 편이 낫다 */
        PARTIAL,
    }

    data class Draft(val screen: ScreenInfo, val source: Source, val usableComment: String?)

    fun draft(hint: ScreenHint): ScreenInfo = analyze(hint).screen

    fun analyze(hint: ScreenHint): Draft {
        val tokens = tokenize(hint.id)
        val translated = translate(tokens)
        val fromComment = hint.comment?.let(::labelFromComment)
        // 모든 토큰을 옮겼는데 주석과 겹치는 말이 없으면 주석이 다른 이야기(옛 화면 이름 등)를 하는 경우가 많다
        val commentIsStale = fromComment != null && translated.text.isNotBlank() && translated.complete &&
            words(fromComment).intersect(words(translated.text)).isEmpty()
        val useComment = fromComment != null && (translated.text.isBlank() || !commentIsStale)
        val label = (if (useComment) fromComment else translated.text).orEmpty().ifBlank { ScreenCatalog.defaultLabel(hint.id) }
        val group = hint.section?.let(::groupFromSection)
            ?: if (tokens.any { it.lowercase() in AUTH_TOKENS }) AUTH_GROUP else translated.firstWord
        val source = when {
            useComment -> Source.COMMENT
            translated.complete -> Source.GLOSSARY
            else -> Source.PARTIAL
        }
        return Draft(ScreenInfo(hint.id, label, group), source, fromComment?.takeIf { useComment })
    }

    // ---- 토큰 ----

    /** `AdminStudyGroupSchedule` → [Admin, Study, Group, Schedule], `QRCodeScan2` → [QR, Code, Scan, 2] */
    fun tokenize(id: String): List<String> =
        Regex("[A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+|\\d+").findAll(id).map { it.value }.toList()

    private data class Translation(val text: String, val complete: Boolean, val firstWord: String?)

    private fun translate(tokens: List<String>): Translation {
        if (tokens.isEmpty()) return Translation("", complete = false, firstWord = null)
        val lower = tokens.map { it.lowercase() }
        val parts = mutableListOf<String>()
        var complete = true
        var i = 0
        while (i < lower.size) {
            // 붙여 쓰는 말(SignUp, QrCode, MyPage)을 먼저 찾는다
            val pair = if (i + 1 < lower.size) glossary[lower[i] + lower[i + 1]] else null
            if (pair != null) {
                parts += pair
                i += 2
                continue
            }
            val word = glossary[lower[i]]
            if (word == null) complete = false
            parts += word ?: tokens[i]
            i++
        }
        // 'FindPassword' 처럼 동사가 앞에 오면 한국어 어순(목적어 + 동사)으로 뒤집는다
        if (parts.size > 1 && lower.first() in LEADING_VERBS) parts.add(parts.removeAt(0))
        // 운영진 전용 화면은 '챌린저 상세 (운영진)' 처럼 뒤에 붙인다 (UMC 원격 설정 README 표기)
        val adminOnly = parts.size > 1 && lower.first() == "admin"
        if (adminOnly) parts.removeAt(0)
        val text = parts.filter { it.isNotEmpty() }.joinToString(" ") + if (adminOnly) " (운영진)" else ""
        return Translation(text, complete, parts.firstOrNull { it.isNotEmpty() && it != "운영진" && it != "내" })
    }

    // ---- 주석 ----

    /**
     * 주석을 화면 이름으로 쓸 수 있으면 다듬어 돌려준다
     *
     * `비밀번호 찾기 (이메일 인증 후 …)` → `비밀번호 찾기`, `프로필 페이지` → `프로필`, `내 qr코드 페이지` → `내 QR 코드`.
     * 문장(…다, …함)이거나 길거나 한글이 없으면 null.
     */
    fun labelFromComment(comment: String): String? {
        var text = comment.trim()
        text = text.substringBefore(". ").substringBefore("(").substringBefore(":").substringBefore("->").trim().trimEnd('.')
        if (text.isEmpty() || !HANGUL.containsMatchIn(text) || text.endsWith("섹션")) return null
        if (SENTENCE_END.containsMatchIn(text) || text.length > MAX_LABEL_LENGTH) return null
        text = text.replace(Regex("\\s*(페이지|화면|단계)$"), "").trim()
        // 영문과 한글 사이를 띄우고 영문 약어는 대문자로 (qr코드 → QR 코드)
        text = text.replace(Regex("([A-Za-z]+)(?=[가-힣])")) { it.value.uppercase() + " " }
        return text.takeIf { it.isNotEmpty() && HANGUL.containsMatchIn(it) }
    }

    /** `홈 화면 섹션` → `홈`, `마이 페이지 섹션` → `MY`, `인증` → `인증` */
    fun groupFromSection(section: String): String? {
        val text = section.trim().removeSuffix("섹션").trim().replace(Regex("\\s*(화면|페이지)$"), "").trim()
        if (text.isEmpty()) return null
        return when (text.replace(" ", "").lowercase()) {
            "마이", "마이페이지", "my", "mypage", "내정보" -> MY_GROUP
            "인증", "시작", "로그인", "auth", "onboarding" -> AUTH_GROUP
            else -> text
        }
    }

    fun words(text: String): Set<String> = text.split(' ', '·').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private val HANGUL = Regex("[가-힣]")
    private val SENTENCE_END = Regex("(다|함|됨|음|요)$|니다|으므로|하면|해야")
    private const val MAX_LABEL_LENGTH = 16

    private val AUTH_TOKENS = setOf("login", "signin", "signup", "sign", "logout", "password", "permission", "splash", "onboarding", "auth", "verify", "verification", "register", "join")
    private val LEADING_VERBS = setOf("find", "create", "edit", "add", "write", "search", "view", "select", "reset", "change", "update", "delete", "register", "verify", "manage")

    /**
     * 안드로이드 화면 이름에 자주 나오는 영어 토큰 → 한국어
     *
     * UMC-PRODUCT/umc-product-android 의 목적지 34개와 흔한 앱 화면 이름에서 모았다. 소문자, 붙여 쓰는 말은 붙여서 쓴다.
     */
    val glossary: Map<String, String> = mapOf(
        // 시작·인증
        "splash" to "시작 화면", "login" to "로그인", "signin" to "로그인", "logout" to "로그아웃",
        "signup" to "회원가입", "register" to "가입", "join" to "가입", "email" to "이메일", "social" to "소셜",
        "password" to "비밀번호", "find" to "찾기", "reset" to "재설정", "verify" to "인증", "verification" to "인증",
        "auth" to "인증", "permission" to "권한 안내", "onboarding" to "온보딩", "terms" to "약관", "fail" to "실패",
        "code" to "코드", "success" to "완료",
        // 공통 동작
        "list" to "목록", "detail" to "상세", "create" to "생성", "add" to "추가", "edit" to "수정", "update" to "수정",
        "write" to "작성", "search" to "검색", "delete" to "삭제", "select" to "선택", "view" to "보기", "manage" to "관리",
        "setting" to "설정", "settings" to "설정", "main" to "메인", "tab" to "탭", "result" to "결과",
        // 도메인
        "home" to "홈", "notification" to "알림", "notifications" to "알림", "alarm" to "알림", "schedule" to "일정",
        "calendar" to "캘린더", "notice" to "공지", "announcement" to "공지", "admin" to "운영진", "act" to "활동",
        "activity" to "활동", "challenger" to "챌린저", "study" to "스터디", "group" to "그룹", "member" to "멤버",
        "attendance" to "출석", "community" to "커뮤니티", "post" to "게시글", "comment" to "댓글", "thread" to "스레드",
        "chat" to "채팅", "chatting" to "채팅", "message" to "메시지", "feed" to "피드", "profile" to "프로필",
        "my" to "내", "mypage" to "마이페이지", "mycard" to "내 명함", "card" to "명함", "received" to "받은",
        "content" to "콘텐츠", "qr" to "QR", "qrcode" to "QR 코드", "scan" to "스캔", "map" to "지도", "event" to "이벤트",
        "payment" to "결제", "order" to "주문", "cart" to "장바구니", "product" to "상품", "review" to "리뷰",
        "report" to "신고", "block" to "차단", "friend" to "친구", "follow" to "팔로우", "bookmark" to "북마크",
        "scrap" to "스크랩", "history" to "기록", "help" to "도움말", "faq" to "자주 묻는 질문", "inquiry" to "문의",
        "version" to "버전",
    )
}
