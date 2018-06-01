package com.serverless;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;

import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.json.Jackson;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Handler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {
	private static final Logger LOG = Logger.getLogger(Handler.class);

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		LOG.info(input);
		LOG.info(input.get("eventName"));
		LOG.info(Jackson.toJsonString(input));
		try {
			if (input.get("Records") != null) {
				s3EventTrigger(input);
				return ApiGatewayResponse.builder().setStatusCode(200).setRawBody("s3EventTrigger ok")
					.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless")).build();
			} else if (input.get("eventName").equals("s3PutImageFromPdfPage")) {
				LOG.info("s3PutImageFromPdfPage");
				s3PutImageFromPdfPage(input);
				return ApiGatewayResponse.builder().setStatusCode(200).setObjectBody ("s3PutImageFromPdfPage ok" + input)
					.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless")).build();
			} else if (input.get("eventName").equals("runConvertPdfToImage")) {
				String index = convertPdfToImage(input);
				return ApiGatewayResponse.builder().setStatusCode(200).setRawBody(index)
					.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless")).build();
			} else {
				// LOG.warn("Unknown payload");
				return ApiGatewayResponse.builder().setStatusCode(400).setRawBody("Unknown payload")
						.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless")).build();
			}
		} catch (IOException e) {
			e.printStackTrace();
			return ApiGatewayResponse.builder().setStatusCode(500).setRawBody("Failed reading PDF file.")
					.setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & serverless")).build();
		}
	}

	private String mainProcess(String bucketName, String key, String extension) throws IOException {
		PDDocument pdf = getPDFDocument(bucketName, key);
		String baseURL = String.format("https://s3.ap-northeast-2.amazonaws.com/%s/%s", bucketName, URLEncoder.encode(key, "UTF-8"));
		LOG.info(baseURL);
		File indexFile = new File(String.format("/tmp/%s.json", key.replace("/", "_")));
		FileWriter osFile = new FileWriter(indexFile);

		JsonObject indexJson = new JsonObject();
		JsonArray indexPages = new JsonArray();
		indexJson.addProperty("title", new File(key).getName());
		indexJson.addProperty("defaultBackgroundImageURI", "");

		int numberOfPages = pdf.getNumberOfPages();
		LOG.info("page is " + numberOfPages);
		for (int pageNum = 0; pageNum < numberOfPages; pageNum++) {
			indexPages.add(subProcess(bucketName, key, pageNum, baseURL, extension));
		}

		indexJson.add("pages", indexPages);

		Gson gson = new Gson();
		osFile.write(gson.toJson(indexJson));
		osFile.close();
		pdf.close();
		
		LOG.info("json: " + gson.toJson(indexJson));
		S3 s3Client = new S3(bucketName, String.format("%s/index.json", key));
		s3Client.putObject(indexFile);

		return gson.toJson(indexJson);
	}

	private JsonObject subProcess(String bucketName, String key, int pageNum, String baseURL, String extension) {
		String pageId = UUID.randomUUID().toString();
		RequestConvert request = new RequestConvert(bucketName, key, pageNum, pageId, extension);
		InvokeResult result = request.invoke();
		// Single.just(result);
		LOG.info(result.toString());
		JsonObject indexPage = new JsonObject();
		String backgroundImageURI = String.format("%s/%d-%s.%s", baseURL, pageNum, pageId, extension);
		indexPage.addProperty("backgroundImageURI", backgroundImageURI);

		return indexPage;
	}

	private PDDocument getPDFDocument(String bucketName, String key) throws IOException {
		LOG.info("getPDFDocument start");
		S3 s3Client = new S3(bucketName, key);
		S3ObjectInputStream objectData = s3Client.getObject();
		LOG.info(key.replace("/", "_"));
		File file = new File("/tmp/" + key.replace("/", "_"));
		FileOutputStream fos = new FileOutputStream(file);
		byte[] read_buf = new byte[1024];
		int read_len = 0;
		while ((read_len = objectData.read(read_buf)) > 0) {
			fos.write(read_buf, 0, read_len);
		}
		objectData.close();
		fos.close();

		LOG.info("getPDFDocument end");
		return PDDocument.load(file);
	}

	private void s3EventTrigger(Map<String, Object> input) throws IOException {
		String inputJson = Jackson.toJsonString(input);
		String extension = "png";
		
		LOG.info(inputJson);
		S3EventNotification s3 = S3EventNotification.parseJson(inputJson);
		List<S3EventNotification.S3EventNotificationRecord> records = s3.getRecords();

		for (S3EventNotification.S3EventNotificationRecord event : records) {
			String bucketName = event.getS3().getBucket().getName();
			String key = URLDecoder.decode(event.getS3().getObject().getKey(), "UTF-8");
			LOG.info(bucketName + key);

			String indexJson = mainProcess(bucketName, key, extension);
		}
	}

	private String convertPdfToImage(Map<String, Object> input) throws IOException {
		String bucketName = (String) input.get("bucketName");
		String key = URLDecoder.decode((String) input.get("key"), "UTF-8");
		String extension = (String) input.get("extension");

		return mainProcess(bucketName, key, extension);
	}

	private void s3PutImageFromPdfPage(Map<String, Object> input) throws IOException {
		String bucketName = (String) input.get("bucketName");
		String key = URLDecoder.decode((String) input.get("key"), "UTF-8");
		int pageNum = (int) input.get("pageNum");
		String pageId = (String) input.get("pageId");
		String extension = (String) input.get("extension");

		PDDocument document = getPDFDocument(bucketName, key);
		LOG.info("getPDFDocument");
		PdfToImg inst = new PdfToImg(document);
		LOG.info("PdfToImg");
		File image = inst.convert(pageNum, extension);
		LOG.info("convert");
		S3 s3Client = new S3(bucketName, String.format("%s/%d-%s.%s", key, pageNum, pageId, extension));
		s3Client.putObject(image);
		LOG.info("putObject");
		document.close();
	}
}
