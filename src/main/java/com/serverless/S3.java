package com.serverless;

import java.io.File;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class S3 {
    public final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
    String key;
    String bucketName;

    public S3(String bucketName, String key) {
        this.bucketName = bucketName;
        this.key = key;
    }

    public void putObject(File file) {
        s3.putObject(bucketName, key, file);
    }

    public S3ObjectInputStream getObject() {
		S3Object object = s3.getObject(bucketName, key);
        S3ObjectInputStream objectData = object.getObjectContent();
        return objectData;
    }
}