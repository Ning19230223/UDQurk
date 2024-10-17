package com.bsy;

import io.quarkus.vertx.web.Route;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FileProcessing {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    @ConfigProperty(name = "quarkus.http.port")
    int port;
    
    @Inject
    @ConfigProperty(name = "file.store.path")
    String filePath;

    @Inject
    Vertx vertx;

    @Route(path = "/fs/index", methods = Route.HttpMethod.GET)
    public void indexFunc(RoutingContext ctx) {
        ctx.response()
                .putHeader("content-type", "text/html")
                .end(
                        "<form action=\"/fs/form\" method=\"post\" enctype=\"multipart/form-data\">\n" +
                                "    <div>\n" +
                                "        <label for=\"name\">Select a file:</label>\n" +
                                "        <input type=\"file\" name=\"file\" />\n" +
                                "    </div>\n" +
                                "    <div class=\"button\">\n" +
                                "        <button type=\"submit\">Send</button>\n" +
                                "    </div>" +
                                "</form>"
                );
    }

    @Route(path = "/fs/form", methods = Route.HttpMethod.POST)
    public void form(RoutingContext ctx) {

        // 支持分块传输编码
        ctx.response().setChunked(true);
        for (FileUpload f : ctx.fileUploads()) {
            // 获取文件名、文件大小、uid
            String uploadedFileName = f.uploadedFileName();
            String originalFileName = f.fileName();
            long fileSize = f.size();
            String uid = uploadedFileName.split("\\\\")[2];

            String fileNameUid = suffixN(uid);
            String fileContextUid = suffixO(uid);
            vertx.fileSystem().writeFile(filePath + fileNameUid, Buffer.buffer(originalFileName));
            vertx.fileSystem().copy(uploadedFileName, filePath + fileContextUid);
            vertx.fileSystem().delete(uploadedFileName);

            
            
            JsonObject jsonObject = new JsonObject()
                    .put("fileName", originalFileName)
                    .put("fileUid", uid)
                    .put("fileSize", fileSize)
                    .put("download url", "http://localhost:" + String.valueOf(port) + "/fs/download/" + uid);
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .write(jsonObject.toString());
        }
        ctx.response().end();
    }

    @Route(path = "/fs/download/:fileUid", methods = Route.HttpMethod.GET)
    public void download(RoutingContext ctx) {
        String fileUid = ctx.pathParam("fileUid");
        String nameFile = suffixN(fileUid);
        String contentFile = suffixO(fileUid);
        String contentFilePath = filePath + contentFile;
        File file = new File(contentFilePath);
        String nameFilePath = filePath + nameFile;
        vertx.fileSystem().readFile(nameFilePath, result -> {
            if(result.succeeded()){
                Buffer buffer = result.result();
                String content = buffer.toString("UTF-8");
                if(file.exists()){
                    ctx.response()
                            .putHeader("Content-Disposition", "attachment; filename=" + content)
                            .sendFile(file.getPath());
                }
            } else {
                logger.error("Failed to read file name: " + result.cause());
                ctx.fail(result.cause());
            }
        });
    }
    
    @Route(path = "/fs/delete/:uid", methods = Route.HttpMethod.GET)
    public void delete(RoutingContext ctx){
        String uid = ctx.pathParam("uid");
        String deleteNamePath = suffixN(filePath + uid);
        String deleteContentPath = suffixO(filePath + uid);
        File fileN = new File(deleteNamePath);
        File fileO = new File(deleteContentPath);
        boolean isDelete1 = fileN.delete();
        boolean isDelete2 = fileO.delete();
        ctx.response().end("Successfully deleted.");
    }
    

    // uid文件名处理方法
    public String suffixN(String fileName) {
        return fileName + "-n";
    }

    public String suffixO(String fileName) {
        return fileName + "-o";
    }
}
