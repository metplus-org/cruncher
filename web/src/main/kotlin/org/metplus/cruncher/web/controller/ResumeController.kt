package org.metplus.cruncher.web.controller

import org.metplus.cruncher.resume.DownloadResumeObserver
import org.metplus.cruncher.resume.DownloadResume
import org.metplus.cruncher.resume.Resume
import org.metplus.cruncher.resume.ResumeFile
import org.metplus.cruncher.resume.UploadResume
import org.metplus.cruncher.resume.UploadResumeObserver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.util.FileCopyUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping(value = [
    "/api/v1/resume",
    "/api/v2/resume"
])
class ResumeController(
        @Autowired private val uploadResume: UploadResume,
        @Autowired private val downloadResume: DownloadResume
) {

    @PostMapping("upload")
    @ResponseBody
    fun uploadResumeEndpoint(@RequestParam("userId") id: String,
                             @RequestParam("name") name: String,
                             @RequestParam("file") file: MultipartFile): CruncherResponse {
        return uploadResume.process(id, name, file.inputStream, file.size, observer = object : UploadResumeObserver<CruncherResponse> {
            override fun onException(exception: Exception, resume: Resume): CruncherResponse {
                return CruncherResponse(
                        resultCode = ResultCodes.FATAL_ERROR,
                        message = "Exception happened while uploading the resume"
                )
            }

            override fun onSuccess(resume: Resume): CruncherResponse {
                return CruncherResponse(
                        resultCode = ResultCodes.SUCCESS,
                        message = "Resume upload successful"
                )
            }

            override fun onEmptyFile(resume: Resume): CruncherResponse {
                return CruncherResponse(
                        resultCode = ResultCodes.FATAL_ERROR,
                        message = "Resume is empty"
                )
            }

        }) as CruncherResponse
    }

    @GetMapping("{userId}")
    @ResponseBody
    fun downloadResumeEndpoint(@PathVariable("userId") id: String, response: HttpServletResponse): CruncherResponse? {
        return downloadResume.process(id, observer = object : DownloadResumeObserver<CruncherResponse?> {
            override fun onError(userId: String, exception: Exception): CruncherResponse {
                return CruncherResponse(
                        resultCode = ResultCodes.FATAL_ERROR,
                        message = "Exception happened while uploading the resume"
                )
            }

            override fun onSuccess(resume: Resume, resumeFile: ResumeFile): CruncherResponse? {
                val outputFile = ByteArrayOutputStream()
                var data = resumeFile.fileStream.read()
                while (data >= 0) {
                    outputFile.write(data.toChar().toInt())
                    data = resumeFile.fileStream.read()
                }
                outputFile.flush()
                response.contentType = "application/octet-stream"
                response.setHeader("Content-Disposition", "inline; filename=\"" + resumeFile.filename + "\"")
                FileCopyUtils.copy(outputFile.toByteArray(), response.outputStream)
                response.setContentLength(outputFile.size())
                response.flushBuffer()
                return null
            }

            override fun onResumeNotFound(userId: String): CruncherResponse {
                return CruncherResponse(
                        resultCode = ResultCodes.FATAL_ERROR,
                        message = "Resume is empty"
                )
            }

        })
    }
}