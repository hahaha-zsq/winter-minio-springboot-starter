package com.zsq.winter.minio.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;

import com.amazonaws.util.Base64;
import com.zsq.winter.minio.config.AmazonS3Properties;
import com.zsq.winter.minio.enums.PolicyType;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AmazonS3Template {
    private final AmazonS3Properties amazonS3Properties;
    private final AmazonS3 amazonS3;

    public AmazonS3Template(final AmazonS3Properties amazonS3Properties, final AmazonS3 amazonS3) {
        this.amazonS3Properties = amazonS3Properties;
        this.amazonS3 = amazonS3;
    }

    /**
     * 存储桶是否存在
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @return boolean
     */
    public boolean existBucket(String bucketName) {
        return this.amazonS3.doesBucketExistV2(bucketName);
    }

    /**
     * 创建bucket
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @return boolean
     */
    public boolean createBucket(String bucketName) {
        if (!this.amazonS3.doesBucketExistV2(bucketName)) {
            Bucket bucket = this.amazonS3.createBucket(bucketName);
            return bucket.getName() != null;
        } else {
            return true;
        }
    }

    /**
     * 创建bucket
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param policyText 策略文本
     * @return boolean
     */
    public boolean createBucket(String bucketName, String policyText) {
        boolean created = this.createBucket(bucketName);
        if (created) {
            this.amazonS3.setBucketPolicy(bucketName, policyText);
        }
        return created;
    }

    /**
     * 创建bucket
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param policyType 策略类型，内置了READ_ONLY、WRITE_ONLY、READ_WRITE
     * @return boolean
     */
    public boolean createBucket(String bucketName, PolicyType policyType) {
        return this.createBucket(bucketName, PolicyType.getPolicy(policyType, bucketName));
    }

    /**
     * 设置bucket策略
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param policyType 策略类型
     */
    public void setBucketPolicy(String bucketName, PolicyType policyType) {
        this.setBucketPolicy(bucketName, PolicyType.getPolicy(policyType, bucketName));
    }

    /**
     * 移除指定名称的存储桶
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     */
    public void removeBucket(String bucketName) {
        this.amazonS3.deleteBucket(bucketName);
    }

    /**
     * 设置bucket策略
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param policyText 策略文本，自己定义的策略，不使用该模块内置的权限(文本策略里面的存储桶名称记得替换成自己的存储桶名称)
     */
    public void setBucketPolicy(String bucketName, String policyText) {
        this.amazonS3.setBucketPolicy(bucketName, policyText);
    }

    /**
     * 获取所有桶
     *
     * @return {@link List}<{@link Bucket}>
     */
    public List<Bucket> getAllBuckets() {
        return this.amazonS3.listBuckets();
    }

    /**
     * 获取指定存储桶
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @return {@link Optional}<{@link Bucket}>
     */
    public Optional<Bucket> getBucket(String bucketName) {
        return this.amazonS3.listBuckets().stream().filter((b) -> {
            return b.getName().equals(bucketName);
        }).findFirst();
    }

    /**
     * 从配置文件获取存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     *
     * @return {@link String}
     */
    public String getBucketName() {
        if (!StringUtils.hasText(this.amazonS3Properties.getBucket())) {
            throw new RuntimeException("未配置默认 BucketName");
        } else {
            return this.amazonS3Properties.getBucket();
        }
    }


    /**
     * 按前缀获取(配置文件中存储桶)所有对象
     *
     * @param prefix    前缀,列出具有特定前缀的对象
     * @param delimiter 分隔符,用于模拟目录结构，分隔符是目录路径的分隔符，默认为空
     * @param maxNum    每次请求返回的最大对象数量，默认是1000
     * @return {@link List}<{@link S3ObjectSummary}>
     */
    public List<S3ObjectSummary> getAllObjectsByPrefix(String prefix, String delimiter, Integer maxNum) {
        return this.getAllObjectsByPrefix(this.getBucketName(), prefix, delimiter, maxNum);
    }

    /**
     * 按前缀获取所有对象，只列出最新版本的对象，即使开启了版本控制，也不会返回已删除的对象（Delete Marker）
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param prefix     前缀,列出具有特定前缀的对象
     * @param delimiter  分隔符,用于模拟目录结构，分隔符是目录路径的分隔符，默认为空
     * @param maxNum     每次请求返回的最大对象数量，默认是1000
     * @return {@link List}<{@link S3ObjectSummary}>
     */
    public List<S3ObjectSummary> getAllObjectsByPrefix(String bucketName, String prefix, String delimiter, Integer maxNum) {
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName) // 指定存储桶名称
                .withPrefix(prefix) // 设置前缀，只列出以 prefix字段值开头的对象，
                // 如果将 withPrefix("") 设置为空字符（空字符串），ListObjectsRequest 将不会限制前缀，而是列出存储桶中的所有对象，包括存储桶根目录下的所有对象。这样会返回存储桶中的所有文件对象，而不仅仅是以某个特定前缀开头的对象。
                .withDelimiter(ObjectUtils.isEmpty(delimiter) ? "" : delimiter) // 设置分隔符，以 '/' 分隔目录和文件
                //例如，如果你有一个对象键为 folder/subfolder/file.txt，使用斜杠作为分隔符，Amazon S3 将会将其分成前缀 folder/ 和对象名称 subfolder/file.txt。
                .withMaxKeys(ObjectUtils.isEmpty(maxNum) ? 1000 : maxNum); // 设置最大返回结果数
        ObjectListing objectListing = amazonS3.listObjects(listObjectsRequest);
        return objectListing.getObjectSummaries();
    }

    public String getPresignedObjectPutUrl(String objectName) {
        return this.getPresignedObjectPutUrl(this.getBucketName(), objectName);
    }

    public String getPresignedObjectPutUrl(String bucketName, String objectName) {
        return this.getPresignedObjectPutUrl(bucketName, objectName, 10);
    }

    public String getPresignedObjectPutUrl(String bucketName, String objectName, Integer time) {
        return this.getPresignedObjectPutUrl(bucketName, objectName, time, TimeUnit.MINUTES);
    }

    public String getPresignedObjectPutUrl(String bucketName, String objectName, Integer time, TimeUnit timeUnit) {
        return this.getObjectUrl(bucketName, objectName, time, timeUnit, HttpMethod.PUT);
    }

    /**
     * 给没有 AWS 权限的用户临时访问 S3 对象的能力
     * 常用于：
     * •	临时下载（GET）
     * •	临时上传（PUT）
     * •	URL 带 签名和过期时间，过期后无法访问
     *
     * @param objectName 对象名称
     * @return {@link String}
     */
    public String getObjectUrl(String objectName) {
        return this.getObjectUrl(this.getBucketName(), objectName);
    }

    /**
     * 给没有 AWS 权限的用户临时访问 S3 对象的能力
     * 常用于：
     * •	临时下载（GET）
     * •	临时上传（PUT）
     * •	URL 带 签名和过期时间，过期后无法访问
     *
     * @param bucketName 存储桶的名称
     * @param objectName 存储桶中的对象名称
     * @return {@link String}
     */
    public String getObjectUrl(String bucketName, String objectName) {
        URL url = this.amazonS3.getUrl(bucketName, objectName);
        return url.toString();
    }

    /**
     * 给没有 AWS 权限的用户临时访问 S3 对象的能力
     * 常用于：
     * •	临时下载（GET）
     * •	临时上传（PUT）
     * •	URL 带 签名和过期时间，过期后无法访问
     *
     * @param objectName 存储桶中的对象名称
     * @param expireTime 过期时间
     * @return {@link String}
     */
    public String getObjectUrl(String objectName, Integer expireTime) {
        return this.getObjectUrl(this.getBucketName(), objectName, expireTime);
    }

    /**
     * 给没有 AWS 权限的用户临时访问 S3 对象的能力
     * 常用于：
     * •	临时下载（GET）
     * •	临时上传（PUT）
     * •	URL 带 签名和过期时间，过期后无法访问
     *
     * @param bucketName 存储桶的名称
     * @param objectName 存储桶中的对象名称
     * @param expireTime 过期时间
     * @return {@link String}
     */
    public String getObjectUrl(String bucketName, String objectName, Integer expireTime) {
        return this.getObjectUrl(bucketName, objectName, expireTime, TimeUnit.MINUTES);
    }

    /**
     * 给没有 AWS 权限的用户临时访问 S3 对象的能力
     * 常用于：
     * •	临时下载（GET）
     * •	临时上传（PUT）
     * •	URL 带 签名和过期时间，过期后无法访问
     *
     * @param bucketName 存储桶的名称
     * @param objectName 存储桶中的对象名称
     * @param expireTime 过期时间
     * @param timeUnit   时间单位
     * @return {@link String}
     */
    public String getObjectUrl(String bucketName, String objectName, Integer expireTime, TimeUnit timeUnit) {
        return this.getObjectUrl(bucketName, objectName, expireTime, timeUnit, HttpMethod.GET);
    }

    /**
     * 给没有 AWS 权限的用户临时访问 S3 对象的能力
     * 常用于：
     * •	临时下载（GET）
     * •	临时上传（PUT）
     * •	URL 带 签名和过期时间，过期后无法访问
     *
     * @param bucketName  存储桶的名称
     * @param objectName  存储桶中的对象名称
     * @param expireTime  过期时间
     * @param timeUnit    时间单位
     * @param contentType 内容类型
     * @return {@link String}
     */
    public String getObjectUrl(String bucketName, String objectName, Integer expireTime, TimeUnit timeUnit, String contentType) {
        return this.getObjectUrl(bucketName, objectName, expireTime, timeUnit, HttpMethod.GET, contentType);
    }

    /**
     * 获取临时对象文件的url
     *
     * @param request 请求
     * @return 字符串
     */
    public String getObjectUrl(GeneratePresignedUrlRequest request) {
        /* amazonS3.generatePresignedUrl(request)是一个Amazon S3 SDK方法，用于生成一个预签名的URL，
        该URL可用于访问Amazon S3中的对象或执行特定操作，如上传、下载或删除对象.
        request参数是一个Amazon S3请求对象，其中包含了生成预签名URL所需的参数，
        如存储桶名称、对象键（文件路径）、HTTP方法（GET、PUT、DELETE等）以及可选的过期时间等。
        生成的预签名URL可以用于临时授权第三方用户访问Amazon S3对象，而无需提供访问凭证（例如AWS密钥）。预签名URL的有效期由过期时间参数控制，
        一旦URL过期，即无法访问。这样可以提供更细粒度的访问控制，或在特定时间范围内授权临时访问，同时避免了将访问凭证直接暴露给用户的风险。
        */
        URL url = this.amazonS3.generatePresignedUrl(request);
        return url.toString();
    }

    /**
     * 获取临时对象文件的url(存在过期时间）
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param expireTime 过期时间
     * @param timeUnit   时间单位
     * @param method     方法
     * @return {@link String}
     */
    public String getObjectUrl(String bucketName, String objectName, Integer expireTime, TimeUnit timeUnit, HttpMethod method, String contentType) {
        /* amazonS3.generatePresignedUrl(request)是一个Amazon S3 SDK方法，用于生成一个预签名的URL，
        该URL可用于访问Amazon S3中的对象或执行特定操作，如上传、下载或删除对象.
        request参数是一个Amazon S3请求对象，其中包含了生成预签名URL所需的参数，
        如存储桶名称、对象键（文件路径）、HTTP方法（GET、PUT、DELETE等）以及可选的过期时间等。
        生成的预签名URL可以用于临时授权第三方用户访问Amazon S3对象，而无需提供访问凭证（例如AWS密钥）。预签名URL的有效期由过期时间参数控制，
        一旦URL过期，即无法访问。这样可以提供更细粒度的访问控制，或在特定时间范围内授权临时访问，同时避免了将访问凭证直接暴露给用户的风险。
        */
        GeneratePresignedUrlRequest generatePresignedUrlRequest = generatePresignedUrlRequest(bucketName, getObjectName((objectName)), contentType, expireTime, timeUnit, method);
        return this.getObjectUrl(generatePresignedUrlRequest);
    }

    /**
     * 获取临时对象文件的url(存在过期时间）
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param objectName 存储桶中的对象名称
     * @param expireTime 过期时间
     * @param timeUnit   时间单位
     * @param method     方法
     * @return {@link String}
     */

    public String getObjectUrl(String bucketName, String objectName, Integer expireTime, TimeUnit timeUnit, HttpMethod method) {
        GeneratePresignedUrlRequest generatePresignedUrlRequest = generatePresignedUrlRequest(bucketName, getObjectName((objectName)), null, expireTime, timeUnit, method);
        return getObjectUrl(generatePresignedUrlRequest);
    }

    /**
     * 生成预签名url请求
     *
     * @param bucketName bucket名称
     * @param objectName 对象名称
     * @param expireTime 过期时间
     * @param timeUnit   时间单位
     * @param method     方法
     * @return 生成预签名url请求
     */
    public GeneratePresignedUrlRequest generatePresignedUrlRequest(String bucketName, String objectName, Integer expireTime, TimeUnit timeUnit, HttpMethod method) {
        return generatePresignedUrlRequest(bucketName, objectName, null, expireTime, timeUnit, method, null);
    }

    /**
     * 生成预签名url请求
     *
     * @param bucketName  bucket名称
     * @param objectName  对象名称
     * @param contentType 内容类型
     * @param expireTime  过期时间
     * @param timeUnit    时间单位
     * @param method      方法
     * @return 生成预签名url请求
     */
    public GeneratePresignedUrlRequest generatePresignedUrlRequest(String bucketName, String objectName, String contentType, Integer expireTime, TimeUnit timeUnit, HttpMethod method) {
        return generatePresignedUrlRequest(bucketName, objectName, contentType, expireTime, timeUnit, method, null);
    }


    /**
     * 生成预签名url请求
     *
     * @param bucketName  bucket名称
     * @param objectName  对象名称
     * @param contentType 内容类型(为空，默认是application/octet-stream)
     * @param expireTime  过期时间
     * @param timeUnit    时间单位
     * @param method      方法
     * @param params      额外的自定义的请求参数(可为空)
     * @return 生成预签名url请求
     */
    public GeneratePresignedUrlRequest generatePresignedUrlRequest(String bucketName, String objectName, String contentType, Integer expireTime, TimeUnit timeUnit, HttpMethod method, Map<String, String> params) {
        if (ObjectUtils.isEmpty(contentType)) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, getObjectName(objectName))
                .withContentType(contentType)
                .withExpiration(formDuration(expireTime, timeUnit)).withMethod(method);
        if (!ObjectUtils.isEmpty(params)) {
            //  request.addRequestParameter是用于向预签名URL请求对象中添加自定义的请求参数的方法.
            //  params.forEach((key, val) -> request.addRequestParameter(key, val));，下面是这行代码的缩写
            params.forEach(request::addRequestParameter);
        }
        return request;
    }

    /**
     * put对象
     * 解决了使用 InputStream 上传对象到 S3 时，原因是 没有指定 Content-Length，AWS SDK 会把整个流缓存在内存里，可能导致 内存占用过高或上传大文件失败的问题
     * 默认使用了stream.available()，可能不可靠，会导致 Content-Length 不准确，请使用它的其他重载方法
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径 如：2024-05-20/img/demo.png
     * @param mediaType  媒体类型
     * @param stream     文件流
     * @param size       读取的字节数的大小，默认为5MB
     * @return {@link PutObjectResult}
     * @throws IOException IOException
     */
    public PutObjectResult putObject(String bucketName, String objectName, String mediaType, InputStream stream, Integer size) throws IOException {
        return this.putObject(bucketName, objectName, mediaType, stream, stream.available(), size);
    }

    /**
     * 上传对象到 S3（安全上传 InputStream，必须指定内容长度）
     *
     * @param bucketName    存储桶名称
     * @param objectName    对象在存储桶中的唯一标识符（文件路径，如：2024-05-20/img/demo.png）
     * @param mediaType     媒体类型
     * @param stream        文件流
     * @param contentLength 文件流字节长度（必须准确，不可使用 stream.available()）
     * @param size          分块大小（默认 5MB）
     * @return {@link PutObjectResult}
     * @throws IOException IOException
     */
    public PutObjectResult putObject(String bucketName, String objectName, String mediaType, InputStream stream, long contentLength, Integer size) throws IOException {
        if (contentLength <= 0) {
            throw new IllegalArgumentException("Content length must be greater than 0. Do not use stream.available()");
        }

        // 设置对象元数据
        ObjectMetadata objectMetadata = new ObjectMetadata();
        /* setContentLength(long contentLength) - 设置对象的大小。
        setContentMD5(String contentMD5) - 设置对象的MD5校验和。
        setContentType(String contentType) - 设置对象的Content-Type。
        setCacheControl(String cacheControl) - 设置对象的缓存控制。
        setExpirationTime(Date expirationTime) - 设置对象的过期时间。
        setExpirationTimeInMillis(long expirationTimeInMillis) - 设置对象的过期时间（以毫秒为单位）。
        setLastModified(Date lastModified) - 设置对象的最后修改时间。
        setLastModified(long lastModified) - 设置对象的最后修改时间（以毫秒为单位）。
        setUserMetadata(Map<String, String> userMetadata) - 设置对象的用户自定义元数据。
        addUserMetadata(String key, String value) - 添加对象的用户自定义元数据。
        getETag() - 获取对象的ETag。
        getContentLength() - 获取对象的大小。
        getContentMD5() - 获取对象的MD5校验和。
        getContentType() - 获取对象的Content-Type。
        getCacheControl() - 获取对象的缓存控制。
        getExpirationTime() - 获取对象的过期时间。
        getLastModified() - 获取对象的最后修改时间。
        getUserMetadata() - 获取对象的用户自定义元数据。
        getUserMetadata(String key) - 获取对象的指定用户自定义元数据。
        */

        //objectMetadata.setContentLength(stream.available()); 解决No content length specified for stream data. Stream contents will be buffered in memory and could
        objectMetadata.setContentLength(contentLength);
        objectMetadata.setContentType(mediaType);

        // 构建 PutObjectRequest
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, getObjectName(objectName), stream, objectMetadata);

        /*
        setReadLimit方法用于指定在单个HTTP请求中可以传输的最大数据量（以字节为单位）。在上传大文件时，Amazon S3客户端可能会将文件分割成多个部分并使用多个请求来上传。
        但是，对于较小的文件，整个文件可能会通过一个HTTP请求上传。setReadLimit方法允许开发者控制这个请求的大小。
        如果上传的文件大小超过这个限制，Amazon S3客户端将使用分块上传（multi-part upload）来上传文件。如果文件大小小于或等于这个限制，文件将通过单个HTTP请求上传。
        这个设置仅影响通过单个请求上传的数据量，并不影响Amazon S3分块上传的默认分块大小。Amazon S3默认的分块大小是5MB
        */
        putObjectRequest.getRequestClientOptions().setReadLimit(ObjectUtils.isEmpty(size) ? 5242880 : size);

        // 上传对象
        return amazonS3.putObject(putObjectRequest);
    }

    /**
     * 该方法接受存储桶名称和对象名称作为参数，并返回一个布尔值来指示对象是否存在。如果对象存在，则返回true；如果对象不存在，则返回false。
     *
     * @param bucketName bucket名称
     * @param objectName 对象名称
     * @return boolean
     */
    public boolean doesObjectExist(String bucketName, String objectName) {
        return this.amazonS3.doesObjectExist(bucketName, objectName);
    }


    /**
     * put对象
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param stream     文件流
     * @return {@link PutObjectResult}
     * @throws IOException IOException
     */
    public PutObjectResult putObject(String bucketName, String objectName, InputStream stream) throws IOException {
        return this.putObject(bucketName, objectName, "application/octet-stream", stream, stream.available() + 1);
    }


    public PutObjectResult putObject(String bucketName, String objectName, InputStream stream, long contentLength, Integer size) throws IOException {
        return this.putObject(bucketName, objectName, "application/octet-stream", stream, contentLength, size);
    }


    /**
     * put对象
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param file       文件
     * @param size       读取的字节数的大小，默认为文件的大小
     * @return {@link PutObjectResult}
     * @throws IOException IOException
     */
    public PutObjectResult putObject(String bucketName, String objectName, MultipartFile file, Integer size) throws IOException {
        // 获取文件名
        String fileName = file.getOriginalFilename();
        long contentLength = file.getSize();
        String mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        return this.putObject(bucketName, objectName, mediaType, file.getInputStream(), contentLength, (ObjectUtils.isEmpty(size) || size == 0) ? file.getInputStream().available() + 1 : size);
    }

    /**
     * put对象
     *
     * @param objectName 对象名称
     * @param file       文件
     * @param size       读取的字节数的大小,默认就是文件的大小
     * @return put对象结果
     * @throws IOException ioexception
     */
    public PutObjectResult putObject(String objectName, MultipartFile file, Integer size) throws IOException {
        return this.putObject(this.getBucketName(), objectName, file, size);
    }

    /**
     * 获取对象信息(配置文件默认桶)
     *
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @return {@link S3Object}
     */
    public S3Object getObjectInfo(String objectName) {
        return this.getObjectInfo(this.getBucketName(), objectName);
    }

    /**
     * 获取对象信息
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @return {@link S3Object}
     */
    public S3Object getObjectInfo(String bucketName, String objectName) {
        return this.amazonS3.getObject(bucketName, getObjectName(objectName));
    }

    /**
     * 启动初始化分块上传操作，它会返回一个 UploadId，标识这个上传会话
     * 使用initiateMultipartUpload方法初始化分块上传后，可以使用UploadId以及其他方法（如uploadPart、completeMultipartUpload等）来管理和操作这个分块上传过程。
     *
     * @param bucketName 存储桶的名称
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param file       文件
     * @return {@link InitiateMultipartUploadResult}
     */
    public InitiateMultipartUploadResult initiateMultipartUpload(String bucketName, String objectName, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String contentType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        return this.initiateMultipartUpload(bucketName, objectName, contentType);
    }

    /**
     * 启动初始化分块上传操作，它会返回一个 UploadId，标识这个上传会话
     * 使用initiateMultipartUpload方法初始化分块上传后，可以使用UploadId以及其他方法（如uploadPart、completeMultipartUpload等）来管理和操作这个分块上传过程。
     *
     * @param bucketName  存储桶的名称
     * @param objectName  对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param contentType 内容类型
     * @return {@link InitiateMultipartUploadResult}
     */
    public InitiateMultipartUploadResult initiateMultipartUpload(String bucketName, String objectName, String contentType) {
        /*
        InitiateMultipartUploadRequest是用于执行分块上传初始化的请求类。它用于指定要进行分块上传的对象的信息，并可以设置相关的请求参数。
        通过创建InitiateMultipartUploadRequest对象，可以设置以下参数：
        BucketName - 要上传的对象所在的存储桶名称。
        Key - 要上传的对象的键（即文件路径）。
        ObjectMetadata - 要上传的对象的元数据，可以设置对象的大小、MD5校验和、Content-Type等属性。
        AccessControlList - 可选项，设置对象的访问控制列表。
        CannedACL - 可选项，设置对象的预定义访问控制权限。
        StorageClass - 可选项，设置对象的存储类别。
        RedirectLocation - 可选项，设置当访问这个对象时的重定向目标URL。
        SSECustomerKey - 可选项，设置服务器端加密的客户提供的密钥。
        SSEAwsKeyManagementParams - 可选项，设置服务器端加密的AWS密钥管理服务参数。
        使用InitiateMultipartUploadRequest对象，可以调用相关的Amazon S3客户端的initiateMultipartUpload方法来执行分块上传初始化操作，并获取相关的UploadId用于后续的分块上传操作。*/
        /*
        initiateMultipartUpload用于初始化分块上传操作。当需要上传大型对象时，可以使用分块上传将对象分成多个部分，并同时上传这些部分。
        initiateMultipartUpload方法创建一个新的MultipartUpload对象，并返回一个相关的UploadId。UploadId是用于标识特定分块上传操作的唯一标识符。
        使用initiateMultipartUpload方法初始化分块上传后，可以使用UploadId以及其他方法（如uploadPart、completeMultipartUpload等）来管理和操作这个分块上传过程。
        分块上传可以提高上传效率，并且在上传过程中如果中断或失败，可以更容易地重试或取消上传，而不需要重新上传整个对象。
        */
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(contentType);
        InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(bucketName, getObjectName(objectName), objectMetadata);
        return this.amazonS3.initiateMultipartUpload(initiateMultipartUploadRequest);
    }

    public InitiateMultipartUploadResult initiateMultipartUpload(String objectName, String contentType) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(contentType);
        InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(this.getBucketName(), getObjectName(objectName), objectMetadata);
        return this.amazonS3.initiateMultipartUpload(initiateMultipartUploadRequest);
    }


    /**
     * 用于执行多部分上传中的单个部分上传操作，上传每一个分块，返回 PartETag
     *
     * @param bucketName  目标S3存储桶的名称
     * @param uploadId    初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     * @param objectName  对象在存储桶中的唯一标识符，可以理解为文件路径(注意这里的objectName必须和初始化分片initiateMultipartUpload使用的objectName一样)
     * @param md5Digest   该部分数据的MD5摘要，用于验证数据完整性
     * @param partNumber  当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的
     * @param partSize    该部分的大小，单位是字节。理想情况下，所有部分大小应该相等，但最后一个部分除外，它可以小一些。
     * @param inputStream 分片文件输入流
     * @return {@link UploadPartResult}
     */
    public UploadPartResult uploadPart(String bucketName, String uploadId, String objectName, String md5Digest, Integer partNumber, long partSize, InputStream inputStream) {
        /*
        bucketName: 目标S3存储桶的名称。
        key: 对象在存储桶中的唯一标识符，可以理解为文件路径。
        uploadId: 不同的分块都必须使用相同的 uploadId，以标识属于同一次上传任务。
        partNumber: 当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的。
        file: 要上传的文件部分的File对象，或者，
        inputStream: 包含要上传数据的InputStream，用于非文件数据或内存中的数据。
        partSize: 分块大小，单位是字节。理想情况下，所有部分大小应该相等，但最后一个部分除外，它可以小一些。
        md5Digest: （可选）该部分数据的MD5摘要，用于验证数据完整性。
        */

        UploadPartRequest uploadPartRequest = (new UploadPartRequest())
                .withBucketName(bucketName)
                .withUploadId(uploadId)
                .withKey(getObjectName(objectName))
                .withMD5Digest(md5Digest)
                .withPartNumber(partNumber)
                .withPartSize(partSize)
                .withInputStream(inputStream);
        return this.amazonS3.uploadPart(uploadPartRequest);
    }

    /**
     * 用于执行多部分上传中的单个部分上传操作(存储桶默认为配置文件的)
     *
     * @param uploadId    初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     * @param objectName  对象在存储桶中的唯一标识符，可以理解为文件路径，可以理解为文件路径(注意这里的objectName必须和初始化分片initiateMultipartUpload使用的objectName一样)
     * @param md5Digest   该部分数据的MD5摘要，用于验证数据完整性
     * @param partNumber  当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的
     * @param partSize    该部分的大小，单位是字节。理想情况下，所有部分大小应该相等，但最后一个部分除外，它可以小一些。
     * @param inputStream 分片文件输入流
     * @return {@link UploadPartResult}
     */
    public UploadPartResult uploadPart(String uploadId, String objectName, String md5Digest, int partNumber, long partSize, InputStream inputStream) {
        return this.uploadPart(this.getBucketName(), uploadId, objectName, md5Digest, partNumber, partSize, inputStream);
    }

    /**
     * 用于执行多部分上传中的单个部分上传操作
     *
     * @param bucketName 目标S3存储桶的名称
     * @param uploadId   初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径，可以理解为文件路径(注意这里的objectName必须和初始化分片initiateMultipartUpload使用的objectName一样)
     * @param partNumber 当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的
     * @param file       分片文件
     * @return {@link UploadPartResult}
     * @throws Exception 例外
     */
    public UploadPartResult uploadPart(String bucketName, String uploadId, String objectName, int partNumber, MultipartFile file) throws Exception {
        String md5Digest;
        long partSize;
        InputStream inputStream;
        try {
            // 生成md5
            byte[] md5s = MessageDigest.getInstance("MD5").digest(file.getBytes());
            md5Digest = Base64.encodeAsString(md5s);
            partSize = file.getSize();
            inputStream = new ByteArrayInputStream(file.getBytes());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new Exception("分块上传MD5加密出错");
        }
        return this.uploadPart(bucketName, uploadId, objectName, md5Digest, partNumber, partSize, inputStream);
    }

    /**
     * 用于执行多部分上传中的单个部分上传操作(存储桶默认为配置文件的)
     *
     * @param uploadId   初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径，可以理解为文件路径(注意这里的objectName必须和初始化分片initiateMultipartUpload使用的objectName一样)
     * @param partNumber 当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的
     * @param file       分片文件
     * @return {@link UploadPartResult}
     * @throws IOException              IOException
     * @throws NoSuchAlgorithmException 没有这样算法例外
     */
    public UploadPartResult uploadPart(String uploadId, String objectName, int partNumber, MultipartFile file) throws Exception {
        return this.uploadPart(this.getBucketName(), uploadId, objectName, partNumber, file);
    }

    /**
     * 列出一个正在进行的分片上传操作的所有已上传部分(存储桶默认为配置文件的)
     * 返回结果包含：
     * •	每个分块的 PartNumber
     * •	每个分块的 ETag
     * •	每个分块的 大小
     * •	上传时间等信息
     * 典型用法：恢复上传（断点续传）
     *
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param uploadId   初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     * @return {@link PartListing}
     */
    public PartListing listParts(String objectName, String uploadId) {
        return this.listParts(this.getBucketName(), objectName, uploadId);
    }

    /**
     * 列出一个正在进行的分片上传操作的所有已上传部分
     * 返回结果包含：
     * •	每个分块的 PartNumber
     * •	每个分块的 ETag
     * •	每个分块的 大小
     * •	上传时间等信息
     * 典型用法：恢复上传（断点续传）
     *
     * @param bucketName 目标S3存储桶的名称
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param uploadId   初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     * @return {@link PartListing}
     */
    public PartListing listParts(String bucketName, String objectName, String uploadId) {
        /*
        bucketName: 目标S3存储桶的名称。
        key: 正在上传的对象的键名。
        uploadId: 初始化多部分上传时返回的唯一标识符。
        maxParts: 单次请求中返回的最大部分数量，默认值为1000，最大值为1000。
        partNumberMarker: 从哪个部分号开始列出，用于分页。如果没有提供，则从第一个部分开始。
        */
        ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, getObjectName(objectName), uploadId);
        return this.amazonS3.listParts(listPartsRequest);
    }

    /**
     * 用于完成一个已开始的分片上传操作。当你上传了一个大文件的所有部分并且所有部分都成功上传后，你需要调用这个方法来通知S3所有部分已就绪，并将它们组合成一个完整的对象。(存储桶为配置文件的)
     *
     * @param objectName 对象名称
     * @param uploadId   上传id
     * @return {@link CompleteMultipartUploadResult}
     */
    public CompleteMultipartUploadResult completeMultipartUpload(String objectName, String uploadId) {
        return this.completeMultipartUpload(this.getBucketName(), objectName, uploadId);
    }

    /**
     * 用于完成一个已开始的分片上传操作。当你上传了一个大文件的所有部分并且所有部分都成功上传后，你需要调用这个方法来通知S3所有部分已就绪，并将它们组合成一个完整的对象。
     * 1.	必须提供所有 PartETag
     * •	每个上传成功的分块都会返回 ETag
     * •	缺少任何一个分块，合并失败
     * 2.	调用完成后不能再次上传或修改该 UploadId
     * •	对应的 Multipart Upload 会被标记为完成
     * •	若有错误需要修改，必须重新发起 Multipart Upload
     * 3.	取消上传用 abortMultipartUpload
     * •	如果上传过程中出现问题或中断，不想完成，可以调用 abortMultipartUpload
     * •	避免占用 S3 存储
     *
     * @param bucketName bucket名称
     * @param objectName 对象名称
     * @param uploadId   上传id
     * @return {@link CompleteMultipartUploadResult}
     */
    public CompleteMultipartUploadResult completeMultipartUpload(String bucketName, String objectName, String uploadId) {
        objectName = getObjectName(objectName);
        PartListing partListing = this.listParts(bucketName, objectName, uploadId);
        List<PartSummary> parts = partListing.getParts();
        return this.completeMultipartUpload(bucketName, objectName, uploadId, parts);
    }


    /**
     * 用于完成一个已开始的分片上传操作。当你上传了一个大文件的所有部分并且所有部分都成功上传后，你需要调用这个方法来通知S3所有部分已就绪，并将它们组合成一个完整的对象。
     * 1.	必须提供所有 PartETag
     * •	每个上传成功的分块都会返回 ETag
     * •	缺少任何一个分块，合并失败
     * 2.	调用完成后不能再次上传或修改该 UploadId
     * •	对应的 Multipart Upload 会被标记为完成
     * •	若有错误需要修改，必须重新发起 Multipart Upload
     * 3.	取消上传用 abortMultipartUpload
     * •	如果上传过程中出现问题或中断，不想完成，可以调用 abortMultipartUpload
     * •	避免占用 S3 存储
     *
     * @param bucketName 目标S3存储桶的名称
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param uploadId   初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     * @param parts      包含了已上传分块的信息。每个分块都被封装为一个PartETag对象，包括分块的编号（part number）和ETag（从amazonS3.listParts()..getParts() 获取）
     * @return {@link CompleteMultipartUploadResult}
     */
    public CompleteMultipartUploadResult completeMultipartUpload(String bucketName, String objectName, String uploadId, List<PartSummary> parts) {
        CompleteMultipartUploadRequest completeMultipartUploadRequest = new CompleteMultipartUploadRequest()
                .withUploadId(uploadId)
                .withKey(getObjectName(objectName))
                .withBucketName(bucketName)
                .withPartETags(parts.stream().map(partSummary -> new PartETag(partSummary.getPartNumber(), partSummary.getETag())).collect(Collectors.toList()));
        return this.amazonS3.completeMultipartUpload(completeMultipartUploadRequest);
    }

    /**
     * 用于取消一个已经开始但未完成的分片上传操作。当用户决定不再继续上传一个大文件，或者上传过程中遇到不可恢复的错误时，调用此方法可以终止上传过程并释放S3中与该上传相关的资源，避免产生相关费用。
     *
     * @param objectName 存储桶中的对象名称
     * @param uploadId   初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     */
    public void abortMultipartUpload(String objectName, String uploadId) {
        this.abortMultipartUpload(this.getBucketName(), objectName, uploadId);
    }

    /**
     * 用于取消一个已经开始但未完成的分片上传操作。当用户决定不再继续上传一个大文件，或者上传过程中遇到不可恢复的错误时，调用此方法可以终止上传过程并释放S3中与该上传相关的资源，避免产生不必要的存储费用。
     *
     * @param bucketName 目标S3存储桶的名称
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param uploadId   初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     */
    public void abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        AbortMultipartUploadRequest abortMultipartUploadRequest = new AbortMultipartUploadRequest(bucketName, getObjectName(objectName), uploadId);
        this.amazonS3.abortMultipartUpload(abortMultipartUploadRequest);
    }

    /**
     * 用于列出指定存储桶中所有正在进行的分片上传作业。这个方法对于管理和监控存储桶中的分片上传是非常有用的，特别是当需要清理未完成的上传或者分析存储桶状态时(存储桶为配置文件中的)。
     * 仅显示 正在进行的或未完成的分块上传，已完成或已中止的 UploadId 不会显示。
     *
     * @param prefix    前缀,列出具有特定前缀的对象
     * @param delimiter 分隔符,用于模拟目录结构，分隔符是目录路径的分隔符
     * @return {@link MultipartUploadListing}
     */
    public MultipartUploadListing listMultipartUploads(String prefix, String delimiter) {
        return this.listMultipartUploads(this.getBucketName(), prefix, delimiter);
    }

    /**
     * 用于列出指定存储桶中所有正在进行的分片上传作业。这个方法对于管理和监控存储桶中的分片上传是非常有用的，特别是当需要清理未完成的上传或者分析存储桶状态时。
     * 仅显示 正在进行的或未完成的分块上传，已完成或已中止的 UploadId
     *
     * @param bucketName bucket名称
     * @param prefix     前缀,列出具有特定前缀的对象
     * @param delimiter  分隔符,用于模拟目录结构，分隔符是目录路径的分隔符
     * @return {@link MultipartUploadListing}
     */
    public MultipartUploadListing listMultipartUploads(String bucketName, String prefix, String delimiter) {
        ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(bucketName)
                .withPrefix(prefix)
                .withDelimiter(delimiter);
        return this.amazonS3.listMultipartUploads(listMultipartUploadsRequest);
    }

    /**
     * 列出指定存储桶中所有正在进行的分片上传作业的详细文件信息
     *
     * @param bucketName bucket名称
     * @param prefix     前缀
     * @param delimiter  分隔符
     * @return {@link List}<{@link Map}<{@link String}, {@link Object}>>
     */
    public List<Map<String, Object>> getMultipartInfoArr(String bucketName, String prefix, String delimiter) {
        MultipartUploadListing multipartUploadListing = this.listMultipartUploads(bucketName, prefix, delimiter);
        // 遍历分块上传列表
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (MultipartUpload multipartUpload : multipartUploadListing.getMultipartUploads()) {
            /*
            Key: 对象在存储桶中的唯一标识符，即上传文件的路径或名称。
            UploadId: 分块上传的唯一标识符。每个分块上传都会生成一个Upload ID，用于在后续的操作中引用这个分块上传任务。
            StorageClass: 存储类型，决定了数据的持久性和可用性。例如，Standard、Standard_IA（标准冰河存储）、OneZone_IA（单可用区冰河存储）等。
            Initiator: 初始化分块上传的实体，通常包含显示名称和ID。
            Owner: 对象的所有者，包含显示名称和ID。
                DisplayName: 拥有者的显示名称，这通常是一个友好的名称，可能是个人或组织的名称。
                ID: 拥有者的唯一标识符，这是一个由Amazon S3分配的字符串，用于唯一地标识拥有者。
            Initiated: 分块上传任务的初始化时间。
            */
            Map<String, Object> map = new HashMap<>();
            map.put("objectName", multipartUpload.getKey());
            map.put("uploadId", multipartUpload.getUploadId());
            map.put("storageClass", multipartUpload.getStorageClass());
            map.put("initiatedDate", multipartUpload.getInitiated());
            map.put("ownerId", multipartUpload.getOwner().getId());
            map.put("ownerDisplayName", multipartUpload.getOwner().getDisplayName());
            dataList.add(map);
            // 可以根据需要处理其他信息，例如存储类、初始化时间等
        }
        return dataList;
    }

    /**
     * 获取对象的网关URL地址
     *
     * @param objectName 存储桶名称
     * @return 获取对象的完整访问URL
     */
    public String getGatewayUrl(String objectName) {
        return this.getGatewayUrl(this.getBucketName(), objectName);
    }


    /**
     * 获取对象的网关URL地址
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 返回对象的完整访问URL
     */
    public String getGatewayUrl(String bucketName, String objectName) {
        objectName = getObjectName(objectName);
        // 如果配置了自定义域名，则直接使用自定义域名拼接对象名
        if (!ObjectUtils.isEmpty(this.amazonS3Properties.getCustomDomain())) {
            return this.amazonS3Properties.getCustomDomain() + "/" + objectName;
        } else {
            // 构建默认的S3访问URL
            String url = this.amazonS3Properties.getEndpoint() + "/" + bucketName;
            // 如果不使用路径风格访问，则转换为虚拟主机风格的端点
            if (this.amazonS3Properties.getPathStyleAccess().equals(Boolean.FALSE)) {
                url = convertToVirtualHostEndpoint(URI.create(this.amazonS3Properties.getEndpoint()), bucketName).toString();
            }

            return url + "/" + objectName;
        }
    }


    public static Date formDuration(Integer time, TimeUnit timeUnit) {
        return Date.from(Instant.now().plus(getDuration(time, timeUnit)));
    }

    public static Date formLocalDateTime(Integer time, TimeUnit timeUnit) {
        return Date.from(getLocalDateTime(time, timeUnit).atZone(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDateTime getLocalDateTime(Integer time, TimeUnit timeUnit) {
        switch (timeUnit) {
            case DAYS:
                return LocalDateTime.now().plusDays((long) time);
            case HOURS:
                return LocalDateTime.now().plusHours((long) time);
            case MINUTES:
                return LocalDateTime.now().plusMinutes((long) time);
            case SECONDS:
                return LocalDateTime.now().plusSeconds((long) time);
            default:
                throw new UnsupportedOperationException("Man, use a real TimeUnit unit");
        }
    }

    public static Duration getDuration(Integer time, TimeUnit timeUnit) {
        switch (timeUnit) {
            case DAYS:
                return Duration.ofDays((long) time);
            case HOURS:
                return Duration.ofHours((long) time);
            case MINUTES:
                return Duration.ofMinutes((long) time);
            case SECONDS:
                return Duration.ofSeconds((long) time);
            case MILLISECONDS:
                return Duration.ofMillis((long) time);
            case NANOSECONDS:
                return Duration.ofNanos((long) time);
            default:
                throw new UnsupportedOperationException("Man, use a real TimeUnit unit");
        }
    }

    /**
     * 获取对象名称(如果对象的长度大于1，且以“/”开头的话，就删除“/”,否则直接返回该对象)
     *
     * @param objectName 对象名称
     * @return {@link String}
     */
    public static String getObjectName(String objectName) {
        return objectName.length() > 1 && objectName.startsWith("/") ? objectName.substring(1) : objectName;
    }

    /**
     * 转换为虚拟主机终结点
     *
     * @param endpoint   一个URI对象，表示S3服务的基本URL，比如 <a href="https://s3.amazonaws.com">...</a>。
     * @param bucketName 存储桶名称
     * @return {@link URI}
     */
    public URI convertToVirtualHostEndpoint(URI endpoint, String bucketName) {
        try {
            //endpoint.getScheme()是从原始端点获取的协议（通常是http或https）。
            //endpoint.getAuthority()是从原始端点获取的权威部分，通常包括主机名和可能的端口号。
            return new URI(String.format("%s://%s.%s", endpoint.getScheme(), bucketName, endpoint.getAuthority()));
        } catch (URISyntaxException var3) {
            throw new IllegalArgumentException("Invalid bucket name: " + bucketName, var3);
        }
    }

    /**
     * 复制对象
     *
     * @param sourceBucketName      源存储桶名称
     * @param sourceKey             源对象键
     * @param destinationBucketName 目标存储桶名称
     * @param destinationKey        目标对象键
     * @return {@link CopyObjectResult}
     */
    public CopyObjectResult copyObject(String sourceBucketName, String sourceKey,
                                       String destinationBucketName, String destinationKey) {
        CopyObjectRequest copyObjectRequest = new CopyObjectRequest(sourceBucketName, sourceKey,
                destinationBucketName, getObjectName(destinationKey));
        return this.amazonS3.copyObject(copyObjectRequest);
    }

    /**
     * 复制对象（使用默认存储桶）
     *
     * @param sourceKey      源对象键
     * @param destinationKey 目标对象键
     * @return {@link CopyObjectResult}
     */
    public CopyObjectResult copyObject(String sourceKey, String destinationKey) {
        return this.copyObject(this.getBucketName(), sourceKey, this.getBucketName(), destinationKey);
    }

    /**
     * 重命名对象（通过复制+删除实现）
     *
     * @param bucketName 存储桶名称
     * @param oldKey     旧对象键
     * @param newKey     新对象键
     */
    public void renameObject(String bucketName, String oldKey, String newKey) {
        // 先复制对象到新位置
        this.copyObject(bucketName, oldKey, bucketName, newKey);
        // 删除原对象
        this.removeObject(bucketName, oldKey);
    }

    /**
     * 重命名对象（使用默认存储桶）
     *
     * @param oldKey 旧对象键
     * @param newKey 新对象键
     */
    public void renameObject(String oldKey, String newKey) {
        this.renameObject(this.getBucketName(), oldKey, newKey);
    }


    /**
     * 删除对象(存储桶默认为配置文件中的存储桶名称)
     *
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     */
    public void removeObject(String objectName) {
        this.removeObject(this.getBucketName(), objectName);
    }

    /**
     * 删除对象
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     */
    public void removeObject(String bucketName, String objectName) {
        this.amazonS3.deleteObject(bucketName, getObjectName(objectName));
    }

    /**
     * 批量删除对象
     *
     * @param bucketName  存储桶名称
     * @param objectNames 对象在存储桶中的唯一标识符，可以理解为文件路径的集合
     * @return {@link DeleteObjectsResult}
     */
    public DeleteObjectsResult removeObjects(String bucketName, List<String> objectNames) {
        List<DeleteObjectsRequest.KeyVersion> keyVersions = objectNames.stream()
                .map(DeleteObjectsRequest.KeyVersion::new)
                .collect(Collectors.toList());

        DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName)
                .withKeys(keyVersions);

        return this.amazonS3.deleteObjects(deleteObjectsRequest);
    }

    /**
     * 批量删除对象（使用默认存储桶）
     *
     * @param keys 要删除的对象键列表
     * @return {@link DeleteObjectsResult}
     */
    public DeleteObjectsResult deleteObjects(List<String> keys) {
        return this.removeObjects(this.getBucketName(), keys);
    }


    /**
     * 获取对象元数据
     *
     * @param bucketName 存储桶名称
     * @param key        对象键
     * @return {@link ObjectMetadata}
     */
    public ObjectMetadata getObjectMetadata(String bucketName, String key) {
        return this.amazonS3.getObjectMetadata(bucketName, getObjectName(key));
    }

    /**
     * 获取对象元数据（使用默认存储桶）
     *
     * @param key 对象键
     * @return {@link ObjectMetadata}
     */
    public ObjectMetadata getObjectMetadata(String key) {
        return this.getObjectMetadata(this.getBucketName(), key);
    }


    /**
     * 设置对象元数据
     *
     * @param bucketName 存储桶名称
     * @param key        对象键
     * @param metadata   元数据
     */
    public void setObjectMetadata(String bucketName, String key, ObjectMetadata metadata) {
        CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucketName, getObjectName(key),
                bucketName, getObjectName(key))
                .withNewObjectMetadata(metadata);
        this.amazonS3.copyObject(copyObjectRequest);
    }

    /**
     * 设置对象元数据（使用默认存储桶）
     *
     * @param key      对象键
     * @param metadata 元数据
     */
    public void setObjectMetadata(String key, ObjectMetadata metadata) {
        this.setObjectMetadata(this.getBucketName(), key, metadata);
    }

    /**
     * 启用存储桶版本控制
     *
     * @param bucketName 存储桶名称
     */
    public void enableBucketVersioning(String bucketName) {
        BucketVersioningConfiguration configuration = new BucketVersioningConfiguration()
                .withStatus(BucketVersioningConfiguration.ENABLED);
        SetBucketVersioningConfigurationRequest request = new SetBucketVersioningConfigurationRequest(bucketName, configuration);
        this.amazonS3.setBucketVersioningConfiguration(request);
    }

    /**
     * 启用存储桶版本控制（使用默认存储桶）
     */
    public void enableBucketVersioning() {
        this.enableBucketVersioning(this.getBucketName());
    }

    /**
     * 禁用存储桶版本控制
     *
     * @param bucketName 存储桶名称
     */
    public void disableBucketVersioning(String bucketName) {
        BucketVersioningConfiguration configuration = new BucketVersioningConfiguration()
                .withStatus(BucketVersioningConfiguration.SUSPENDED);
        SetBucketVersioningConfigurationRequest request = new SetBucketVersioningConfigurationRequest(bucketName, configuration);
        this.amazonS3.setBucketVersioningConfiguration(request);
    }

    /**
     * 禁用存储桶版本控制（使用默认存储桶）
     */
    public void disableBucketVersioning() {
        this.disableBucketVersioning(this.getBucketName());
    }

    /**
     * 列出对象版本
     *
     * @param bucketName 存储桶名称
     * @param prefix     前缀
     * @return {@link VersionListing}
     */
    public VersionListing listVersions(String bucketName, String prefix) {
        ListVersionsRequest request = new ListVersionsRequest()
                .withBucketName(bucketName)
                .withPrefix(prefix);
        return this.amazonS3.listVersions(request);
    }

    /**
     * 列出对象版本（使用默认存储桶）
     *
     * @param prefix 前缀
     * @return {@link VersionListing}
     */
    public VersionListing listVersions(String prefix) {
        return this.listVersions(this.getBucketName(), prefix);
    }

    /**
     * 设置存储桶生命周期配置
     *
     * @param bucketName 存储桶名称
     * @param config     生命周期配置
     */
    public void setBucketLifecycleConfiguration(String bucketName, BucketLifecycleConfiguration config) {
        this.amazonS3.setBucketLifecycleConfiguration(bucketName, config);
    }

    /**
     * 设置存储桶生命周期配置（使用默认存储桶）
     *
     * @param config 生命周期配置
     */
    public void setBucketLifecycleConfiguration(BucketLifecycleConfiguration config) {
        this.setBucketLifecycleConfiguration(this.getBucketName(), config);
    }

    /**
     * 获取存储桶生命周期配置
     *
     * @param bucketName 存储桶名称
     * @return {@link BucketLifecycleConfiguration}
     */
    public BucketLifecycleConfiguration getBucketLifecycleConfiguration(String bucketName) {
        return this.amazonS3.getBucketLifecycleConfiguration(bucketName);
    }

    /**
     * 获取存储桶生命周期配置（使用默认存储桶）
     *
     * @return {@link BucketLifecycleConfiguration}
     */
    public BucketLifecycleConfiguration getBucketLifecycleConfiguration() {
        return this.getBucketLifecycleConfiguration(this.getBucketName());
    }

    /**
     * 下载对象到文件
     *
     * @param bucketName 存储桶名称
     * @param key        对象键
     * @param file       目标文件
     * @throws IOException IOException
     */
    public void downloadObject(String bucketName, String key, File file) throws IOException {
        S3Object s3Object = this.amazonS3.getObject(bucketName, getObjectName(key));
        try (InputStream inputStream = s3Object.getObjectContent();
             FileOutputStream outputStream = new FileOutputStream(file);
             ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream)) {

            outputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        }
    }

    /**
     * 下载对象到文件（使用默认存储桶）
     *
     * @param key  对象键
     * @param file 目标文件
     * @throws IOException IOException
     */
    public void downloadObject(String key, File file) throws IOException {
        this.downloadObject(this.getBucketName(), key, file);
    }

    /**
     * 获取对象输入流
     *
     * @param bucketName 存储桶名称
     * @param key        对象键
     * @return {@link InputStream}
     */
    public InputStream getObjectInputStream(String bucketName, String key) {
        S3Object s3Object = this.amazonS3.getObject(bucketName, getObjectName(key));
        return s3Object.getObjectContent();
    }

    /**
     * 获取对象输入流（使用默认存储桶）
     *
     * @param key 对象键
     * @return {@link InputStream}
     */
    public InputStream getObjectInputStream(String key) {
        return this.getObjectInputStream(this.getBucketName(), key);
    }

    /**
     * 获取对象输入流（带范围）
     *
     * @param bucketName 存储桶名称
     * @param key        对象键
     * @param start      开始位置
     * @param end        结束位置
     * @return {@link InputStream}
     */
    public InputStream getObjectInputStream(String bucketName, String key, long start, long end) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, getObjectName(key))
                .withRange(start, end);
        S3Object s3Object = this.amazonS3.getObject(getObjectRequest);
        return s3Object.getObjectContent();
    }

    /**
     * 获取对象输入流（带范围，使用默认存储桶）
     *
     * @param key   对象键
     * @param start 开始位置
     * @param end   结束位置
     * @return {@link InputStream}
     */
    public InputStream getObjectInputStream(String key, long start, long end) {
        return this.getObjectInputStream(this.getBucketName(), key, start, end);
    }


    /**
     * 设置存储桶通知配置
     *
     * @param bucketName 存储桶名称
     * @param config     通知配置
     */
    public void setBucketNotificationConfiguration(String bucketName, BucketNotificationConfiguration config) {
        this.amazonS3.setBucketNotificationConfiguration(bucketName, config);
    }

    /**
     * 设置存储桶通知配置（使用默认存储桶）
     *
     * @param config 通知配置
     */
    public void setBucketNotificationConfiguration(BucketNotificationConfiguration config) {
        this.setBucketNotificationConfiguration(this.getBucketName(), config);
    }

    /**
     * 获取存储桶通知配置
     *
     * @param bucketName 存储桶名称
     * @return {@link BucketNotificationConfiguration}
     */
    public BucketNotificationConfiguration getBucketNotificationConfiguration(String bucketName) {
        return this.amazonS3.getBucketNotificationConfiguration(bucketName);
    }

    /**
     * 获取存储桶通知配置（使用默认存储桶）
     *
     * @return {@link BucketNotificationConfiguration}
     */
    public BucketNotificationConfiguration getBucketNotificationConfiguration() {
        return this.getBucketNotificationConfiguration(this.getBucketName());
    }


    /**
     * 检查对象是否存在（使用默认存储桶）
     *
     * @param objectName 对象名称
     * @return boolean
     */
    public boolean doesObjectExist(String objectName) {
        return this.doesObjectExist(this.getBucketName(), objectName);
    }

    /**
     * 获取对象大小
     *
     * @param bucketName 存储桶名称
     * @param key        对象键
     * @return 对象大小（字节）
     */
    public long getObjectSize(String bucketName, String key) {
        ObjectMetadata metadata = this.getObjectMetadata(bucketName, key);
        return metadata.getContentLength();
    }

    /**
     * 获取对象大小（使用默认存储桶）
     *
     * @param key 对象键
     * @return 对象大小（字节）
     */
    public long getObjectSize(String key) {
        return this.getObjectSize(this.getBucketName(), key);
    }

    /**
     * 获取对象最后修改时间
     *
     * @param bucketName 存储桶名称
     * @param key        对象键
     * @return 最后修改时间
     */
    public Date getObjectLastModified(String bucketName, String key) {
        ObjectMetadata metadata = this.getObjectMetadata(bucketName, key);
        return metadata.getLastModified();
    }

    /**
     * 获取对象最后修改时间（使用默认存储桶）
     *
     * @param key 对象键
     * @return 最后修改时间
     */
    public Date getObjectLastModified(String key) {
        return this.getObjectLastModified(this.getBucketName(), key);
    }
}
