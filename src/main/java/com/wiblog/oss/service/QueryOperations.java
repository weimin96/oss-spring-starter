package com.wiblog.oss.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.StringUtils;
import com.wiblog.oss.bean.ObjectInfo;
import com.wiblog.oss.bean.ObjectTreeNode;
import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.util.Util;
import org.apache.tika.Tika;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 查询操作类
 *
 * @author panwm
 * @since 2023/8/20 21:40
 */
public class QueryOperations extends Operations {

    public QueryOperations(AmazonS3 amazonS3, OssProperties ossProperties) {
        super(ossProperties, amazonS3);
    }

    /**
     * 测试是否连接成功
     *
     * @return boolean
     */
    public boolean testConnect() {
        try {
            amazonS3.listObjects(new ListObjectsRequest(ossProperties.getBucketName(), null, null, null, 1));
            return true;
        } catch (AmazonServiceException e) {
            return false;
        }
    }

    /**
     * 判断桶是否存在
     *
     * @param bucketName 桶名称
     * @return boolean
     */
    public boolean testConnectForBucket(String bucketName) {
        try {
            return amazonS3.doesBucketExistV2(bucketName);
        } catch (AmazonServiceException e) {
            return false;
        }
    }

    /**
     * 判断桶是否存在
     *
     * @return boolean
     */
    public boolean testConnectForBucket() {
        return testConnectForBucket(ossProperties.getBucketName());
    }

    /**
     * 获取全部bucket
     *
     * @return Bucket列表
     */
    public List<Bucket> getAllBuckets() {
        return amazonS3.listBuckets();
    }

    /**
     * 根据文件前置查询文件
     *
     * @param path 文件目录
     * @return Object信息列表
     */
    public List<ObjectInfo> listObjects(String path) {
        return listObjects(ossProperties.getBucketName(), path);
    }

    /**
     * 根据文件前置查询文件
     *
     * @param path       文件目录
     * @param bucketName 桶名称
     * @return Object信息列表
     */
    public List<ObjectInfo> listObjects(String bucketName, String path) {
        List<S3ObjectSummary> s3ObjectSummaries = listObjectSummary(bucketName, path, null);
        return s3ObjectSummaries.stream().map(e -> ObjectInfo.builder()
                .uri(e.getKey())
                .url(getDomain() + e.getKey())
                .name(Util.getFilename(e.getKey()))
                .uploadTime(e.getLastModified())
                .build()).collect(Collectors.toList());
    }

    /**
     * 根据文件前置查询文件列表
     *
     * @param path 文件目录
     * @return Object列表
     */
    public List<S3ObjectSummary> listObjectSummary(String path) {
        return listObjectSummary(ossProperties.getBucketName(), path, null);
    }

    /**
     * 根据文件前置查询文件列表
     *
     * @param path       文件目录
     * @param bucketName 桶名称
     * @return Object列表
     */
    public List<S3ObjectSummary> listObjectSummary(String bucketName, String path, String keyword) {
        path = Util.formatPath(path);
        // 列出存储桶中的对象
        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucketName).withPrefix(path);

        List<S3ObjectSummary> objects = new ArrayList<>();
        ListObjectsV2Result response;

        do {
            response = amazonS3.listObjectsV2(request);
            List<S3ObjectSummary> collect;
            if (StringUtils.isNullOrEmpty(keyword)) {
                collect = response.getObjectSummaries().stream().collect(Collectors.toList());
            } else {
                collect = response.getObjectSummaries().stream().filter(e -> e.getKey().contains(keyword)).collect(Collectors.toList());
            }
            objects.addAll(collect);

            if (response.isTruncated()) {
                String token = response.getNextContinuationToken();
                request.setContinuationToken(token);
            }
        } while (response.isTruncated());

        return objects;
    }

    /**
     * 获取下一层级目录树
     * @param path 路径
     * @return List
     */
    public List<ObjectTreeNode> listNextLevel(String path) {
        return listNextLevel(ossProperties.getBucketName(), path);
    }

    /**
     * 获取下一层级目录树
     * @param bucketName 桶名称
     * @param path 路径
     * @return List
     */
    public List<ObjectTreeNode> listNextLevel(String bucketName, String path) {
        path = Util.formatPath(path);
        // 列出存储桶中的对象
        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucketName).withPrefix(path).withDelimiter("/");

        List<S3ObjectSummary> objects = new ArrayList<>();
        List<String> commonPrefixes = new ArrayList<>();
        ListObjectsV2Result response = null;

        do {
            response = amazonS3.listObjectsV2(request);
            objects.addAll(response.getObjectSummaries());
            commonPrefixes.addAll(response.getCommonPrefixes());

            if (response.isTruncated()) {
                String token = response.getNextContinuationToken();
                request.setContinuationToken(token);
            }
        } while (response.isTruncated());
        List<ObjectTreeNode> folders = commonPrefixes.stream().map(this::buildTreeNode).collect(Collectors.toList());
        List<ObjectTreeNode> files = objects.stream().filter(e -> e.getSize() > 0).map(this::buildObjectInfo).collect(Collectors.toList());
        List<ObjectTreeNode> resultList = new ArrayList<>(folders);
        resultList.addAll(files);
        return resultList;
    }

    /**
     * 校验文件是否存在
     *
     * @param bucketName 桶名称
     * @param objectName 文件全路径
     * @return ObjectInfo对象信息
     */
    public boolean checkExist(String bucketName, String objectName) {
        // 判断对象（Object）是否存在。
        return amazonS3.doesObjectExist(bucketName, objectName);
    }

    /**
     * 校验文件是否存在
     *
     * @param objectName 文件全路径
     * @return ObjectInfo对象信息
     */
    public boolean checkExist(String objectName) {
        // 判断对象（Object）是否存在。
        return checkExist(ossProperties.getBucketName(), objectName);
    }

    /**
     * 获取文件信息
     *
     * @param objectName 文件全路径
     * @return ObjectInfo对象信息
     */
    public ObjectInfo getObjectInfo(String objectName) {
        return getObjectInfo(ossProperties.getBucketName(), objectName);
    }

    /**
     * 获取文件信息
     *
     * @param bucketName 桶名称
     * @param objectName 文件全路径
     * @return ObjectInfo对象信息
     */
    public ObjectInfo getObjectInfo(String bucketName, String objectName) {
        S3Object object = getS3Object(bucketName, objectName);
        return buildObjectInfo(object);
    }

    /**
     * 获取文件信息
     *
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @return S3Object
     */
    public S3Object getS3Object(String bucketName, String objectName) {
        try {
            if (objectName.startsWith("/")) {
                objectName = objectName.substring(1);
            }
            return amazonS3.getObject(bucketName, objectName);
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                // 文件不存在，返回空值
                return null;
            } else {
                // 其他异常，继续抛出
                throw e;
            }
        }
    }

    /**
     * 获取文本内容
     *
     * @param objectName 文件全路径
     * @return String 文本
     */
    public String getContent(String objectName) {
        return getContent(ossProperties.getBucketName(), objectName);
    }

    /**
     * 获取文本内容
     *
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @return InputStream 文件流
     */
    public String getContent(String bucketName, String objectName) {
        InputStream inputStream = getInputStream(bucketName, objectName);
        if (inputStream == null) {
            return null;
        }
        try {
            return IOUtils.toString(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取文件流
     *
     * @param objectName 文件全路径
     * @return InputStream 文件流
     */
    public InputStream getInputStream(String objectName) {
        return getInputStream(ossProperties.getBucketName(), objectName);
    }

    /**
     * 获取文件流
     *
     * @param bucketName 存储桶
     * @param objectName 文件全路径
     * @return InputStream 文件流
     */
    public InputStream getInputStream(String bucketName, String objectName) {
        S3Object s3Object = getS3Object(bucketName, objectName);
        if (s3Object == null) {
            return null;
        }
        return s3Object.getObjectContent().getDelegateStream();
    }

    /**
     * 下载文件
     *
     * @param objectName    文件全路径
     * @param localFilePath 存放位置
     * @return File
     */
    public File getFile(String objectName, String localFilePath) {
        return getFile(ossProperties.getBucketName(), objectName, localFilePath);
    }

    /**
     * 下载文件
     *
     * @param bucketName    存储桶
     * @param objectName    文件全路径
     * @param localFilePath 存放位置
     * @return File
     */
    public File getFile(String bucketName, String objectName, String localFilePath) {
        S3Object s3Object = getS3Object(bucketName, objectName);
        File outputFile = new File(localFilePath);
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        String filename = Util.getFilename(objectName);
        Util.formatPath(localFilePath);
        if (!Util.checkIsFile(localFilePath)) {
            if (!outputFile.exists()) {
                outputFile.mkdirs();
            }
            localFilePath = Util.formatPath(localFilePath);
            outputFile = new File(localFilePath + filename);
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = s3Object.getObjectContent().read(buffer)) != -1) {

                fos.write(buffer, 0, bytesRead);
            }
            s3Object.getObjectContent().close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputFile;
    }

    /**
     * 下载文件夹
     *
     * @param objectName    文件全路径
     * @param localFilePath 存放位置
     */
    public void getFolder(String objectName, String localFilePath) {
        getFolder(ossProperties.getBucketName(), objectName, localFilePath);
    }

    /**
     * 下载文件夹
     *
     * @param bucketName    存储桶
     * @param objectName    文件全路径
     * @param localFilePath 存放位置
     */
    public void getFolder(String bucketName, String objectName, String localFilePath) {
        List<S3ObjectSummary> s3ObjectSummaries = listObjectSummary(bucketName, objectName, null);
        if (!localFilePath.endsWith(File.pathSeparator)) {
            localFilePath += File.separator;
        }
        try {
            for (S3ObjectSummary objectSummary : s3ObjectSummaries) {
                S3Object s3Object = amazonS3.getObject(objectSummary.getBucketName(), objectSummary.getKey());
                S3ObjectInputStream inputStream = s3Object.getObjectContent();
                String filepath;
                String slash = "/".equals(File.separator) ? "/" : "\\\\";
                String key = objectSummary.getKey().replace(objectName + "/", "").replaceAll("/", slash);
                filepath = localFilePath + key;
                Util.copyInputStreamToFile(inputStream, filepath);

                s3Object.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 预览文件
     *
     * @param response   响应
     * @param objectName 文件全路径
     * @throws IOException io异常
     */
    public void previewObject(HttpServletResponse response, String objectName) throws IOException {
        if (StringUtils.isNullOrEmpty(objectName)) {
            return;
        }
        if (objectName.contains("%")) {
            objectName = URLDecoder.decode(objectName, "UTF-8");
        }

        try (S3Object s3Object = amazonS3.getObject(ossProperties.getBucketName(), objectName)) {
            // 设置响应头信息
            String filename = Util.getFilename(s3Object.getKey());
            Tika tika = new Tika();
            response.setContentType(tika.detect(filename));
            response.setContentLength((int) s3Object.getObjectMetadata().getContentLength());
            response.setHeader("Content-Disposition", "inline; filename=" + filename);
            // 设置响应内容类型为
            try (InputStream inputStream = s3Object.getObjectContent();
                 OutputStream outputStream = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.setHeader("content-type","text/html;charset=utf-8");
                // 文件不存在
                response.getWriter().println("<html><head><title>404 Not Found</title></head><body><h1>404 Not Found</h1></body></html>");
            } else {
                // 其他异常，继续抛出
                throw e;
            }
        } catch (IOException e) {
            if ("Broken pipe".equals(e.getMessage())) {
                return;
            }
            throw new IOException(e);
        }
    }



    /**
     * 获取目录结构
     *
     * @param path 目录
     * @return 树形结构
     */
    public ObjectTreeNode getTreeList(String path) {
        return getTreeList(ossProperties.getBucketName(), path);
    }

    /**
     * 获取目录结构
     *
     * @param bucketName 存储桶
     * @param path       目录
     * @return 树形结构
     */
    public ObjectTreeNode getTreeList(String bucketName, String path) {
        List<S3ObjectSummary> objects = listObjectSummary(bucketName, path, null);
        return buildTree(objects, path);
    }

    /**
     * 获取目录结构
     * @param bucketName 存储桶
     * @param path 目录
     * @param keyword 关键字
     * @return 树形结构
     */
    public ObjectTreeNode getTreeListByName(String bucketName, String path, String keyword) {
        List<S3ObjectSummary> objects = listObjectSummary(bucketName, path, keyword);
        return buildTree(objects, path);
    }

    /**
     * 获取目录结构
     * @param path 目录
     * @param keyword 关键字
     * @return 树形结构
     */
    public ObjectTreeNode getTreeListByName(String path, String keyword) {
        return getTreeListByName(ossProperties.getBucketName(), path, keyword);
    }

    private ObjectTreeNode buildTree(List<S3ObjectSummary> objectList, String objectName) {
        String rootName;
        if (StringUtils.isNullOrEmpty(objectName)) {
            rootName = "";
        } else {
            int i = objectName.lastIndexOf("/");
            rootName = (i > 0) ? objectName.substring(i + 1) : objectName;
        }

        ObjectTreeNode root = new ObjectTreeNode(rootName, objectName, getDomain() + objectName, null, "folder", 0, null);

        for (S3ObjectSummary object : objectList) {
            if (object.getKey().startsWith(objectName + "/")) {
                String remainingPath = object.getKey().substring(objectName.length() + 1);
                addNode(root, remainingPath, object);
            } else if (StringUtils.isNullOrEmpty(objectName)) {
                addNode(root, object.getKey(), object);
            }
        }

        return root;
    }

    private void addNode(ObjectTreeNode parentNode, String remainingPath, S3ObjectSummary object) {
        int slashIndex = remainingPath.indexOf('/');
        if (slashIndex == -1) { // 文件节点
            if (StringUtils.isNullOrEmpty(remainingPath)) {
                return;
            }
            ObjectTreeNode fileNode = new ObjectTreeNode(remainingPath, object.getKey(), getDomain() + object.getKey(),
                    object.getLastModified(), "file", object.getSize(), Util.getExtension(object.getKey()));
            parentNode.addChild(fileNode);
        } else { // 文件夹节点
            String folderName = remainingPath.substring(0, slashIndex);
            String newRemainingPath = remainingPath.substring(slashIndex + 1);

            // 在当前节点的子节点中查找是否已存在同名文件夹节点
            ObjectTreeNode folderNode = findFolderNode(parentNode.getChildren(), folderName);
            if (folderNode == null) { // 若不存在，则创建新的文件夹节点
                String uri = StringUtils.isNullOrEmpty(parentNode.getUri()) ? folderName : parentNode.getUri() + "/" + folderName;
                folderNode = new ObjectTreeNode(folderName, uri, getDomain() + uri, null, "folder", 0, null);
                parentNode.addChild(folderNode);
            }

            addNode(folderNode, newRemainingPath, object);
        }
    }

    private ObjectTreeNode findFolderNode(List<ObjectTreeNode> nodes, String folderName) {
        if (nodes == null) {
            return null;
        }
        for (ObjectTreeNode node : nodes) {
            if (node.getName().equals(folderName) && "folder".equals(node.getType())) {
                return node;
            }
        }
        return null;
    }
}
