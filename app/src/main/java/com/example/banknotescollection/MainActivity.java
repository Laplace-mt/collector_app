package com.example.banknotescollection;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText priceEditText;
    private EditText serialNumberEditText;
    private Button submitButton;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        priceEditText = findViewById(R.id.price);
        serialNumberEditText = findViewById(R.id.serialNumber);
        submitButton = findViewById(R.id.button);

        submitButton.setOnClickListener(v -> {

            String priceText = priceEditText.getText().toString().trim();
            String serialText = serialNumberEditText.getText().toString().trim();

            if (priceText.isEmpty() || serialText.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
                return; // Прерываем выполнение
            }

            if (!serialText.matches("^[А-Яа-яЁё]{2}\\d{7}$")) {
                Toast.makeText(this, "Неверный формат! Нужно 2 буквы и 7 цифр (например, Аб1112223)", Toast.LENGTH_LONG).show();
                return;
            }

            try {
                int price = Integer.parseInt(priceText);
                String series = serialText.substring(0, 2);
                String number = serialText.substring(2);

                // Блокируем кнопку, чтобы пользователь не нажал её 10 раз подряд, пока идет запрос
                submitButton.setEnabled(false);
                submitButton.setText("Отправка...");

                // Отправляем запрос в фоновом потоке
                sendToServer(price, series, number);

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Данные некорректны", Toast.LENGTH_SHORT).show();
            }

        });
    }
    private void sendToServer(int price, String series, String number) {
        executorService.execute(() -> {
            String responseMessage;
            boolean isSuccess = false;

            try {
                // ВАЖНО: Если вы запускаете приложение на ЭМУЛЯТОРЕ Android Studio,
                // используйте адрес "http://10.0.2.2:8000/api/add" вместо 192.168.0.111
                // 10.0.2.2 - это специальный алиас для хост-машины (вашего компьютера) из эмулятора.
                URL url = new URL("http://109.196.164.164:8000/api/add");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true); // Разрешаем отправку данных (тела запроса)
                connection.setConnectTimeout(5000); // Таймаут подключения 5 сек
                connection.setReadTimeout(5000);    // Таймаут чтения 5 сек

                // Формируем JSON: {"price": 1000, "series": "Аб", "number": "1112223"}
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("price", price);
                jsonParam.put("series", series);
                jsonParam.put("number", number);

                // Отправляем JSON на сервер
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonParam.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Получаем код ответа (200, 201, 404, 500 и т.д.)
                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                    responseMessage = "Данные успешно отправлены! (Код: " + responseCode + ")";
                    isSuccess = true;
                } else {
                    responseMessage = "Ошибка сервера. Код ответа: " + responseCode;
                }

                connection.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
                responseMessage = "Ошибка сети: " + e.getMessage();
            }

            // Возвращаемся в главный поток, чтобы обновить UI (показать Toast и разблокировать кнопку)
            final String finalMessage = responseMessage;
            final boolean finalSuccess = isSuccess;

            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, finalMessage, Toast.LENGTH_LONG).show();

                // Разблокируем кнопку
                submitButton.setEnabled(true);
                submitButton.setText("Отправить");

                // Если всё хорошо, очищаем поля ввода
                if (finalSuccess) {
                    priceEditText.setText("");
                    serialNumberEditText.setText("");
                }
            });
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Правильно завершаем работу пула потоков при закрытии Activity
        executorService.shutdown();
    }
}