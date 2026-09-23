package io.github.rudtjr1106.switchboard.app.platform

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** 지우기는 되돌릴 수 없으니, 링크를 따라가지 않고 정해진 자리 밖은 건드리지 않는다 */
object SafeFiles {

    /**
     * 폴더를 통째로 지운다. 심볼릭 링크는 따라가지 않고 링크 자체만 지운다
     *
     * `File.deleteRecursively()` 와 `Files.walk(…, FOLLOW_LINKS)` 는 폴더 링크 안으로 들어간다.
     * 업데이트는 `/Applications` 옆에서 도는 작업이라, 링크를 따라가면 사용자의 다른 앱을 지울 수 있다
     * (실제로 이 저장소의 DMG 작업에서 일어났던 사고다).
     */
    fun deleteTree(root: Path) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(root)) {
            Files.delete(root)
            return
        }
        // walkFileTree 는 기본적으로 링크를 따라가지 않는다 (FOLLOW_LINKS 를 주지 않았다)
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
}
