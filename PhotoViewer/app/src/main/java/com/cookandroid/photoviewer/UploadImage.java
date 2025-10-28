package com.cookandroid.photoviewer;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class UploadImage extends AppCompatActivity {

    EditText editTitle;
    EditText editText;
    ImageView imagePreview;
    Button btnSelectImage;
    Button btnSubmit;
    ProgressBar progressBar;

    ActivityResultLauncher<String> mGetContent;

    // 선택된 이미지의 경로(Uri)를 저장할 변수
    private Uri selectedImageUri = null;

    // MainActivity에서 복사해 온 상수
    String site_url = "http://10.0.2.2:8000";
    String token = "e199ffe6b1a308f3a53290c59ef55ff0f92527a2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.upload_image);

        editTitle = findViewById(R.id.edit_title);
        editText = findViewById(R.id.edit_text);
        imagePreview = findViewById(R.id.image_preview);
        btnSelectImage = findViewById(R.id.btn_select_image);
        btnSubmit = findViewById(R.id.btn_submit);
        progressBar = findViewById(R.id.progress_bar);

        mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            selectedImageUri = uri; // Uri 저장
                            imagePreview.setImageURI(selectedImageUri); // 이미지뷰에 표시
                            imagePreview.setVisibility(View.VISIBLE); // 이미지뷰 보이기
                        }
                    }
                });
    }

    public void onClickSelectImage(View v) {
        mGetContent.launch("image/*");
    }

    public void onClickSubmit(View v) {
        String title = editTitle.getText().toString();
        String text = editText.getText().toString();

        if (title.isEmpty() || text.isEmpty()) {
            Toast.makeText(this, "제목과 내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedImageUri == null) {
            Toast.makeText(this, "이미지를 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        Date now = new Date();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        String publishedDate = sdf.format(now);

        Log.d("UploadImage", "Sending Date: " + publishedDate); // 디버깅 로그

        new PutPost().execute(selectedImageUri, title, text, publishedDate);
    }

    private class PutPost extends AsyncTask<Object, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE); // 프로그레스바 보이기
            btnSubmit.setEnabled(false); // 버튼 비활성화
            btnSelectImage.setEnabled(false);
        }

        @Override
        protected String doInBackground(Object... params) {
            Uri imageUri = (Uri) params[0];
            String title = (String) params[1];
            String text = (String) params[2];
            String publishedDate = (String) params[3];

            String apiUrl = site_url + "/api_root/Post/";

            HttpURLConnection conn = null;
            DataOutputStream dos = null;
            InputStream fileInputStream = null;
            StringBuilder response = new StringBuilder();

            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****" + System.currentTimeMillis() + "*****";

            try {
                fileInputStream = getContentResolver().openInputStream(imageUri);
                String fileName = getFileName(imageUri);

                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();

                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                dos = new DataOutputStream(conn.getOutputStream());

                writeMultiPartText(dos, boundary, "title", title);
                writeMultiPartText(dos, boundary, "text", text);
                writeMultiPartText(dos, boundary, "published_date", publishedDate);

                // --- 이미지 파일 전송 ---
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"" + fileName + "\"" + lineEnd);
                dos.writeBytes("Content-Type: " + getContentResolver().getType(imageUri) + lineEnd);
                dos.writeBytes(lineEnd);

                int bytesAvailable = fileInputStream.available();
                int bufferSize = Math.min(bytesAvailable, 1024 * 1024);
                byte[] buffer = new byte[bufferSize];
                int bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                while (bytesRead > 0) {
                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, 1024 * 1024);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }
                dos.writeBytes(lineEnd);
                // --- 전송 완료 ---
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                dos.flush();

                // --- 응답 받기 ---
                int responseCode = conn.getResponseCode();
                InputStream is;
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    is = conn.getInputStream();
                } else {
                    is = conn.getErrorStream();
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                is.close();

                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    return "Upload Success (201):\n" + response.toString();
                } else {
                    return "Upload Failed (" + responseCode + "):\n" + response.toString();
                }

            } catch (IOException e) {
                e.printStackTrace();
                return "Upload Error: " + e.getMessage();
            } finally {
                if (fileInputStream != null) { try { fileInputStream.close(); } catch (IOException e) { e.printStackTrace(); } }
                if (dos != null) { try { dos.close(); } catch (IOException e) { e.printStackTrace(); } }
                if (conn != null) { conn.disconnect(); }
            }
        }

        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.GONE); // 프로그레스바 숨기기
            btnSubmit.setEnabled(true); // 버튼 다시 활성화
            btnSelectImage.setEnabled(true);

            Log.d("PutPost", result);
            Toast.makeText(UploadImage.this, result, Toast.LENGTH_LONG).show();

            if (result.contains("Success")) {
                setResult(Activity.RESULT_OK);
                finish();
            }
        }


        private void writeMultiPartText(DataOutputStream dos, String boundary, String name, String value) throws IOException {
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + lineEnd);
            dos.writeBytes("Content-Type: text/plain; charset=UTF-8" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.write(value.getBytes("UTF-8"));
            dos.writeBytes(lineEnd);
        }

        private String getFileName(Uri uri) {
            String result = null;
            if (uri.getScheme().equals("content")) {
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (index >= 0) {
                            result = cursor.getString(index);
                        }
                    }
                }
            }
            if (result == null) {
                result = uri.getPath();
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
            return result != null ? result : "uploaded_file.jpg";
        }
    }
}