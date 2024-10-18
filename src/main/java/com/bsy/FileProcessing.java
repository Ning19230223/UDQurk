package com.bsy;

import io.quarkus.vertx.web.Route;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.PreparedStatement;


public class FileProcessing {

    final Logger logger = LoggerFactory.getLogger(getClass());
    
    String originalFileName;

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
            originalFileName = f.fileName();
            long fileSize = f.size();
            String uid = "bsy-" + uploadedFileName.split("\\\\")[2];

            String fileNameUid = suffixN(uid);
            String fileContextUid = suffixO(uid);
            vertx.fileSystem().writeFile(filePath + fileNameUid, Buffer.buffer(originalFileName));
            vertx.fileSystem().copy(uploadedFileName, filePath + fileContextUid);
            vertx.fileSystem().delete(uploadedFileName);
            
            // 返回JSON对象
//            JsonObject jsonObject = new JsonObject()
//                    .put("fileName", originalFileName)
//                    .put("fileUid", uid)
//                    .put("fileSize", fileSize)
//                    .put("download url", "http://localhost:" + String.valueOf(port) + "/fs/download/" + uid);
//            ctx.response()
//                    .putHeader("Content-Type", "application/json")
//                    .write(jsonObject.toString());
            ctx.response().write(uid);
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
    
//    @Route(path = "/fs/info/:uid", methods = Route.HttpMethod.GET)
//    public void info(RoutingContext ctx) {
//        String uid = ctx.pathParam("uid");
//        String fileNamePath = suffixN(filePath + uid);
//        String fileContentPath = suffixO(filePath + uid);
//        File file = new File(fileNamePath);
//        File fileContent = new File(fileContentPath);
//        ctx.response().setChunked(true);
//        vertx.fileSystem().readFile(fileNamePath, result -> {
//            if(result.succeeded()){
//                Buffer buffer = result.result();
//                String content = buffer.toString("UTF-8");
//                
//                JsonObject jsonOBject = new JsonObject()
//                        .put("name", content)
//                        .put("size", fileContent.length())
//                        .put("uid", uid);
//                ctx.response()
//                        .putHeader("Content-Type", "application/json")
//                        .write(jsonOBject.toString());
//            } else {
//                logger.error("Failed to read file name: " + result.cause());
//                ctx.fail(result.cause());
//            }
//        });
//        
//        
//    }

    @Route(path = "/fs/info/:uid", methods = Route.HttpMethod.GET)
    public void info(RoutingContext ctx) {
        String uid = ctx.pathParam("uid");
        String fileNamePath = suffixN(filePath + uid);
        String fileContentPath = suffixO(filePath + uid);
        File file = new File(fileNamePath);
        File fileContent = new File(fileContentPath);

        vertx.fileSystem().readFile(fileNamePath, result -> {
            if(result.succeeded()){
                Buffer buffer = result.result();
                String line = buffer.toString("UTF-8");
                String suffix = line.split("\\.")[1];

                Path path = Paths.get(fileNamePath);
                String createTime = "00:00";
                try{
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    FileTime creationTime = attrs.creationTime();
                    createTime = creationTime.toString();
                } catch(IOException e){
                    logger.error("Failed to read file attributes", e);
                }
                
                JsonObject jsonObject = new JsonObject()
                        .put("name", line)
                        .put("size", fileContent.length())
                        .put("suffix", suffix)
                        .put("uid", uid)
                        .put("time", createTime);

                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(jsonObject.toString());

            } else {
                logger.error("Failed to read file name: " + result.cause());
                ctx.fail(result.cause());
            }
        });
    }
    

    // uid文件名处理方法
    public String suffixN(String fileName) {
        return fileName + "-n";
    }

    public String suffixO(String fileName) {
        return fileName + "-o";
    }
}
