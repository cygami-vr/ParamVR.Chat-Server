package chat.paramvr

import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import java.awt.MediaTracker
import java.awt.Panel
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

fun PipelineContext<Unit, ApplicationCall>.scale(imgData: ByteArray, maxSize: Int, dest: Path) {

    // Toolkit gets colors correct more often than ImageIO
    val img = Toolkit.getDefaultToolkit().createImage(imgData!!)

    // Unfortunately haven't found a better way than creating this unused panel
    val tracker = MediaTracker(Panel())

    tracker.addImage(img, 0)
    tracker.waitForID(0)

    val origWidth = img.getWidth(null)
    val origHeight = img.getHeight(null)

    val targetWidth = if (origWidth > origHeight) maxSize else (maxSize * origWidth.toDouble() / origHeight).toInt()
    val targetHeight = if (origHeight > origWidth) maxSize else (maxSize * origHeight.toDouble() / origWidth).toInt()

    log("Original = ${origWidth}x$origHeight, Target = ${targetWidth}x$targetHeight")

    val bufferedImg = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = bufferedImg.graphics
    graphics.drawImage(img, 0, 0, targetWidth, targetHeight, null)
    graphics.dispose()

    ImageIO.write(bufferedImg, "png", dest.toFile())
}