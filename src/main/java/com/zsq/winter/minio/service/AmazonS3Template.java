package com.zsq.winter.minio.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ListMultipartUploadsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListPartsRequest;
import com.amazonaws.services.s3.model.MultipartUpload;
import com.amazonaws.services.s3.model.MultipartUploadListing;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.PartListing;
import com.amazonaws.services.s3.model.PartSummary;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import com.amazonaws.util.Base64;
import com.zsq.winter.minio.config.AmazonS3Properties;
import com.zsq.winter.minio.enums.PolicyType;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
     * @param policyType 策略类型
     * @return boolean
     */
    public boolean createBucket(String bucketName, PolicyType policyType) {
        boolean created = this.createBucket(bucketName);
        if (created) {
            this.amazonS3.setBucketPolicy(bucketName, PolicyType.getPolicy(policyType, bucketName));
        }
        return created;
    }

    /**
     * 设置bucket策略
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     * @param policyType 策略类型
     */
    public void setBucketPolicy(String bucketName, PolicyType policyType) {
        this.amazonS3.setBucketPolicy(bucketName, PolicyType.getPolicy(policyType, bucketName));
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
     * 移除指定名称的存储桶
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
     */
    public void removeBucket(String bucketName) {
        this.amazonS3.deleteBucket(bucketName);
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
     * 按前缀获取所有对象
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

    public String getObjectUrl(String objectName) {
        return this.getObjectUrl(this.getBucketName(), objectName);
    }

    public String getObjectUrl(String bucketName, String objectName) {
        URL url = this.amazonS3.getUrl(bucketName, objectName);
        return url.toString();
    }

    public String getObjectUrl(String objectName, Integer time) {
        return this.getObjectUrl(this.getBucketName(), objectName, time);
    }

    public String getObjectUrl(String bucketName, String objectName, Integer time) {
        return this.getObjectUrl(bucketName, objectName, time, TimeUnit.MINUTES);
    }

    public String getObjectUrl(String bucketName, String objectName, Integer time, TimeUnit timeUnit) {
        return this.getObjectUrl(bucketName, objectName, time, timeUnit, HttpMethod.GET);
    }

    public String getObjectUrl(String bucketName, String objectName, Integer time, TimeUnit timeUnit,String contentType) {
        return this.getObjectUrl(bucketName, objectName, time, timeUnit, HttpMethod.GET,contentType);
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
    public String getObjectUrl(String bucketName, String objectName, Integer expireTime, TimeUnit timeUnit, HttpMethod method,String contentType) {
        /* amazonS3.generatePresignedUrl(request)是一个Amazon S3 SDK方法，用于生成一个预签名的URL，
        该URL可用于访问Amazon S3中的对象或执行特定操作，如上传、下载或删除对象.
        request参数是一个Amazon S3请求对象，其中包含了生成预签名URL所需的参数，
        如存储桶名称、对象键（文件路径）、HTTP方法（GET、PUT、DELETE等）以及可选的过期时间等。
        生成的预签名URL可以用于临时授权第三方用户访问Amazon S3对象，而无需提供访问凭证（例如AWS密钥）。预签名URL的有效期由过期时间参数控制，
        一旦URL过期，即无法访问。这样可以提供更细粒度的访问控制，或在特定时间范围内授权临时访问，同时避免了将访问凭证直接暴露给用户的风险。
        */
        GeneratePresignedUrlRequest generatePresignedUrlRequest = generatePresignedUrlRequest(bucketName, getObjectName((objectName)), contentType, expireTime, timeUnit, method);
        return getObjectUrl(generatePresignedUrlRequest);
    }
    public String getObjectUrl(String bucketName, String objectName, Integer expireTime, TimeUnit timeUnit, HttpMethod method) {
        /* amazonS3.generatePresignedUrl(request)是一个Amazon S3 SDK方法，用于生成一个预签名的URL，
        该URL可用于访问Amazon S3中的对象或执行特定操作，如上传、下载或删除对象.
        request参数是一个Amazon S3请求对象，其中包含了生成预签名URL所需的参数，
        如存储桶名称、对象键（文件路径）、HTTP方法（GET、PUT、DELETE等）以及可选的过期时间等。
        生成的预签名URL可以用于临时授权第三方用户访问Amazon S3对象，而无需提供访问凭证（例如AWS密钥）。预签名URL的有效期由过期时间参数控制，
        一旦URL过期，即无法访问。这样可以提供更细粒度的访问控制，或在特定时间范围内授权临时访问，同时避免了将访问凭证直接暴露给用户的风险。
        */
        GeneratePresignedUrlRequest generatePresignedUrlRequest = generatePresignedUrlRequest(bucketName, getObjectName((objectName)),null, expireTime, timeUnit, method);
        return getObjectUrl(generatePresignedUrlRequest);
    }
    /**
     * 获取临时对象文件的url
     *
     * @param request 请求
     * @return 字符串
     */
    public String getObjectUrl(GeneratePresignedUrlRequest request) {
        URL url = this.amazonS3.generatePresignedUrl(request);
        return url.toString();
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
    public GeneratePresignedUrlRequest generatePresignedUrlRequest(String bucketName, String objectName, Integer expireTime, TimeUnit timeUnit, HttpMethod method){
        return generatePresignedUrlRequest(bucketName, objectName,null, expireTime, timeUnit, method, null);
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
    public GeneratePresignedUrlRequest generatePresignedUrlRequest(String bucketName, String objectName, String contentType,Integer expireTime, TimeUnit timeUnit, HttpMethod method){
        return generatePresignedUrlRequest(bucketName, objectName,contentType, expireTime, timeUnit, method, null);
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
    public GeneratePresignedUrlRequest generatePresignedUrlRequest(String bucketName, String objectName,String contentType, Integer expireTime, TimeUnit timeUnit, HttpMethod method,Map<String, String> params){
        if(ObjectUtils.isEmpty(contentType)){
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
        objectMetadata.setContentLength(stream.available());
        objectMetadata.setContentType(mediaType);
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, getObjectName(objectName), stream, objectMetadata);
         /*
        setReadLimit方法用于指定在单个HTTP请求中可以传输的最大数据量（以字节为单位）。在上传大文件时，Amazon S3客户端可能会将文件分割成多个部分并使用多个请求来上传。
        但是，对于较小的文件，整个文件可能会通过一个HTTP请求上传。setReadLimit方法允许开发者控制这个请求的大小。
        如果上传的文件大小超过这个限制，Amazon S3客户端将使用分块上传（multi-part upload）来上传文件。如果文件大小小于或等于这个限制，文件将通过单个HTTP请求上传。
        这个设置仅影响通过单个请求上传的数据量，并不影响Amazon S3分块上传的默认分块大小。Amazon S3默认的分块大小是5MB
        */
        putObjectRequest.getRequestClientOptions().setReadLimit(ObjectUtils.isEmpty(size) ? 5242880 : size);

        return this.amazonS3.putObject(putObjectRequest);
    }

    /**
     * 该方法接受存储桶名称和对象名称作为参数，并返回一个布尔值来指示对象是否存在。如果对象存在，则返回true；如果对象不存在，则返回false。
     *
     * @param bucketName bucket名称
     * @param objectName 对象名称
     * @return boolean
     */
    public boolean doesObjectExist(String bucketName, String objectName){
        return this.amazonS3.doesObjectExist(bucketName,objectName);
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
        String mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        return this.putObject(bucketName, objectName, mediaType, file.getInputStream(), (ObjectUtils.isEmpty(size)||size==0)?file.getInputStream().available()+1:size);
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
        // 获取文件名
        String fileName = file.getOriginalFilename();
        String mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        return this.putObject(getBucketName(), objectName, mediaType, file.getInputStream(), (ObjectUtils.isEmpty(size)||size==0)?file.getInputStream().available()+1:size);
    }

    /**
     * 获取对象信息
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
     * 启动多部分上传
     *
     * @param bucketName 存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
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
     * 启动初始化分块上传操作
     *
     * @param bucketName  存储桶的名称，即对象将要从中被删除的Amazon S3存储桶
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


    /**
     * 用于执行多部分上传中的单个部分上传操作
     *
     * @param bucketName  目标S3存储桶的名称
     * @param uploadId    初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程（从InitiateMultipartUploadResult获取）
     * @param objectName  对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param md5Digest   该部分数据的MD5摘要，用于验证数据完整性
     * @param partNumber  当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的
     * @param partSize    该部分的大小，单位是字节。理想情况下，所有部分大小应该相等，但最后一个部分除外，它可以小一些。
     * @param inputStream 文件输入流
     * @return {@link UploadPartResult}
     */
    public UploadPartResult uploadPart(String bucketName, String uploadId, String objectName, String md5Digest, Integer partNumber, long partSize, InputStream inputStream) {
        /*
        bucketName: 目标S3存储桶的名称。
        key: 对象在存储桶中的唯一标识符，可以理解为文件路径。
        uploadId: 初始化多部分上传时返回的唯一标识符，用于跟踪整个多部分上传过程。
        partNumber: 当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的。
        file: 要上传的文件部分的File对象，或者，
        inputStream: 包含要上传数据的InputStream，用于非文件数据或内存中的数据。
        partSize: 该部分的大小，单位是字节。理想情况下，所有部分大小应该相等，但最后一个部分除外，它可以小一些。
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
     * @param objectName  对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param md5Digest   该部分数据的MD5摘要，用于验证数据完整性
     * @param partNumber  当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的
     * @param partSize    该部分的大小，单位是字节。理想情况下，所有部分大小应该相等，但最后一个部分除外，它可以小一些。
     * @param inputStream 输入流
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
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param partNumber 当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的
     * @param file       文件
     * @return {@link UploadPartResult}
     * @throws Exception 例外
     */
    public UploadPartResult uploadPart(String bucketName, String uploadId, String objectName, int partNumber, MultipartFile file) throws Exception {
        String md5Digest;
        long partSize;
        InputStream inputStream;
        try {
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
     * @param objectName 对象在存储桶中的唯一标识符，可以理解为文件路径
     * @param partNumber 当前上传部分的编号，必须是1到10000之间的整数，每个部分编号是唯一的
     * @param file       文件
     * @return {@link UploadPartResult}
     * @throws IOException              IOException
     * @throws NoSuchAlgorithmException 没有这样算法例外
     */
    public UploadPartResult uploadPart(String uploadId, String objectName, int partNumber, MultipartFile file) throws IOException, NoSuchAlgorithmException {
        byte[] md5s = MessageDigest.getInstance("MD5").digest(file.getBytes());
        String md5Digest = Base64.encodeAsString(md5s);
        long partSize = file.getSize();
        InputStream inputStream = new ByteArrayInputStream(file.getBytes());
        return this.uploadPart(this.getBucketName(), uploadId, objectName, md5Digest, partNumber, partSize, inputStream);
    }

    public PartListing listParts(String objectName, String uploadId) {
        return this.listParts(this.getBucketName(), objectName, uploadId);
    }

    /**
     * 列出一个正在进行的分片上传操作的所有已上传部分
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

    public String getGatewayUrl(String objectName) {
        return this.getGatewayUrl(this.getBucketName(), objectName);
    }

    public String getGatewayUrl(String bucketName, String objectName) {
        objectName = getObjectName(objectName);
        if (!ObjectUtils.isEmpty(this.amazonS3Properties.getCustomDomain())) {
            return this.amazonS3Properties.getCustomDomain() + "/" + objectName;
        } else {
            String url = this.amazonS3Properties.getEndpoint() + "/" + bucketName;
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
    public static URI convertToVirtualHostEndpoint(URI endpoint, String bucketName) {
        try {
            //endpoint.getScheme()是从原始端点获取的协议（通常是http或https）。
            //endpoint.getAuthority()是从原始端点获取的权威部分，通常包括主机名和可能的端口号。
            return new URI(String.format("%s://%s.%s", endpoint.getScheme(), bucketName, endpoint.getAuthority()));
        } catch (URISyntaxException var3) {
            throw new IllegalArgumentException("Invalid bucket name: " + bucketName, var3);
        }
    }
}
