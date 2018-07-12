package com.serverless;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class S3 {
    public final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
    String bucketName;

    public S3(String bucketName) {
        this.bucketName = bucketName;
    }

    public void putObject(String key, File file) {
        s3.putObject(bucketName, key, file);
    }

    public S3ObjectInputStream getObject(String key) {
		S3Object object = s3.getObject(bucketName, key);
        S3ObjectInputStream objectData = object.getObjectContent();
        return objectData;
    }

    public File readFile(String key) throws IOException {
        S3ObjectInputStream objectData = getObject(key);
		File file = new File("/tmp/" + key.replace("/", "_"));
		FileOutputStream fos = new FileOutputStream(file);
		byte[] read_buf = new byte[1024];
		int read_len = 0;
		while ((read_len = objectData.read(read_buf)) > 0) {
			fos.write(read_buf, 0, read_len);
		}
		objectData.close();
		fos.close();

        return file;
    }
}