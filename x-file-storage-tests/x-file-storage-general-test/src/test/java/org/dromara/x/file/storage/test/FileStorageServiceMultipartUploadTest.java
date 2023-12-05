package org.dromara.x.file.storage.test;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Assert;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.ProgressListener;
import org.dromara.x.file.storage.core.constant.Constant;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class FileStorageServiceMultipartUploadTest {

    @Autowired
    private FileStorageService fileStorageService;

    public File getFile() throws IOException {
        String url = "https://app.xuyanwu.cn/BadApple/video/Bad%20Apple.mp4";

        File file = new File(System.getProperty("java.io.tmpdir"), "Bad Apple.mp4");
        if (!file.exists()) {
            log.info("测试手动分片上传文件不存在，正在下载中");
            FileUtil.writeFromStream(new URL(url).openStream(), file);
            log.info("测试手动分片上传文件下载完成");
        }
        return file;
    }

    /**
     * 测试手动分片上传
     */
    @Test
    public void upload() throws IOException {
        File file = getFile();

        String defaultPlatform = fileStorageService.getDefaultPlatform();
        if (!fileStorageService.isSupportMultipartUpload(defaultPlatform)) {
            log.info("手动分片上传文件结束，当前存储平台【{}】不支持此功能", defaultPlatform);
            return;
        }

        FileInfo fileInfo = fileStorageService
                .initiateMultipartUpload()
                .setPath("test/")
                .setOriginalFilename(file.getName())
                .setSaveFilename("BadApple.mp4")
                .setSize(file.length())
                .setObjectId("0")
                .setObjectType("user")
                .putAttr("user", "admin")
                .putMetadata(Constant.Metadata.CONTENT_DISPOSITION, "attachment;filename=DownloadFileName.mp4")
                .putMetadata("Test-Not-Support", "123456") // 测试不支持的元数据
                .putUserMetadata("role", "666")
                .setFileAcl(Constant.ACL.PRIVATE)
                .init();

        log.info("手动分片上传文件初始化成功：{}", fileInfo);

        try (BufferedInputStream in = FileUtil.getInputStream(file)) {
            for (int partNumber = 1; ; partNumber++) {
                byte[] bytes = IoUtil.readBytes(in, 5 * 1024 * 1024); // 每个分片大小 5MB
                if (bytes == null || bytes.length == 0) break;

                int finalPartNumber = partNumber;
                fileStorageService
                        .uploadPart(fileInfo, partNumber, bytes, (long) bytes.length)
                        .setProgressListener(new ProgressListener() {
                            @Override
                            public void start() {
                                System.out.println("分片 " + finalPartNumber + " 上传开始");
                            }

                            @Override
                            public void progress(long progressSize, Long allSize) {
                                if (allSize == null) {
                                    System.out.println("分片 " + finalPartNumber + " 已上传 " + progressSize + " 总大小未知");
                                } else {
                                    System.out.println("分片 " + finalPartNumber + " 已上传 " + progressSize + " 总大小"
                                            + allSize + " " + (progressSize * 10000 / allSize * 0.01) + "%");
                                }
                            }

                            @Override
                            public void finish() {
                                System.out.println("分片 " + finalPartNumber + " 上传结束");
                            }
                        })
                        .upload();
            }
        }

        List<FilePartInfo> partList = fileStorageService.listParts(fileInfo).listParts();
        for (FilePartInfo info : partList) {
            log.info("手动分片上传-列举已上传的分片：{}", info);
        }

        fileStorageService
                .completeMultipartUpload(fileInfo)
                //                .setPartInfoList(partList)
                .complete();
        log.info("手动分片上传文件完成成功：{}", fileInfo);

        //        fileStorageService.delete(fileInfo);
    }

    /**
     * 测试手动分片上传后取消
     */
    @Test
    public void abort() throws IOException {
        String defaultPlatform = fileStorageService.getDefaultPlatform();
        if (!fileStorageService.isSupportMultipartUpload(defaultPlatform)) {
            log.info("手动分片上传文件结束，当前存储平台【{}】不支持此功能", defaultPlatform);
            return;
        }

        File file = getFile();

        FileInfo fileInfo = fileStorageService
                .initiateMultipartUpload()
                .setPath("test/")
                .setSaveFilename("BadApple.mp4")
                .init();

        log.info("手动分片上传文件初始化成功：{}", fileInfo);

        try (BufferedInputStream in = FileUtil.getInputStream(file)) {
            for (int partNumber = 1; ; partNumber++) {
                byte[] bytes = IoUtil.readBytes(in, 5 * 1024 * 1024); // 每个分片大小 5MB
                if (bytes == null || bytes.length == 0) break;
                System.out.println("分片 " + partNumber + " 上传开始");
                fileStorageService
                        .uploadPart(fileInfo, partNumber, bytes, (long) bytes.length)
                        .upload();
                System.out.println("分片 " + partNumber + " 上传完成");
            }
        }

        List<FilePartInfo> partList = fileStorageService.listParts(fileInfo).listParts();
        for (FilePartInfo info : partList) {
            log.info("手动分片上传-列举已上传的分片：{}", info);
        }

        fileStorageService.abortMultipartUpload(fileInfo).abort();
        log.info("手动分片上传文件已取消，正在验证：{}", fileInfo);
        try {
            partList = null;
            partList = fileStorageService.listParts(fileInfo).listParts();
        } catch (Exception e) {
        }
        Assert.isTrue(CollUtil.isEmpty(partList), "手动分片上传文件取消失败！");
        log.info("手动分片上传文件取消成功：{}", fileInfo);
    }
}