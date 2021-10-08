package com.example.rssidatalogger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    TextView textSampleCount;
    ListView listWifiScan;
    TextView textCountDown;
    TextView textPointNumber;
    Button btnStartStop;
    Button btnShowHide;

    Context context;

    WifiManager wifiManager;
    BroadcastReceiver wifiScanReceiver;

    boolean scanStarted = false;
    boolean showAll = false;

    //timer para realizar mostrar tempo restante para a proxima medida
    private CountDownTimer timer;

    //Lista con os resultados de um escanamento wifi
    private List<ScanResult> scanResults;
    //Array para mostrar a lista na tela
    private ArrayList<String> arrayList = new ArrayList<>();
    private ArrayAdapter adapter;


    //objetos necessários para repetir as medidas periodicamente
    private Runnable mRunnable;
    private Handler mHandler = new Handler();

    //tempo entre escaneamentos
    private int timeInterval = 1000;
    private int tickInterval = 500;

    //nome do arquivo a ser salvo
    private String filename;

    //contador de quantas amostras foram feitas
    private int samples = 0 ;
    String pointNumber = "0";



    List<String> filenamesList = Arrays.asList(
            "VIVO_5GHz",
            "VIVO_2_4GHz",
            "OI",
            "CELULAR"
    );
    List<String> routersMacAddress = Arrays.asList(
            "f4:54:20:5d:4c:3e",
            "f4:54:20:5d:4c:3f",
            "c8:5a:9f:e8:e2:c7",
            "7e:8b:b5:4b:e4:f3"
    );
    Map<String, String> routers = new HashMap<>();
    String fileSuffix;

    long lastTimeStamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textSampleCount = findViewById(R.id.textSampleCount);
        listWifiScan= findViewById(R.id.listWifiScan);
        textCountDown= findViewById(R.id.textCountDown);
        btnStartStop = findViewById(R.id.btnStart);
        btnShowHide= findViewById(R.id.btnShow);
        textPointNumber = findViewById(R.id.textPointNumber);

        context = getApplicationContext();

        timer = new CountDownTimer(timeInterval,tickInterval) {
            @Override
            public void onTick(long millisUntilFinished) {
                textCountDown.setText( String.format("%.2f",(millisUntilFinished / 1000f)));
            }

            @Override
            public void onFinish() {
                textCountDown.setText("!");
                this.cancel();
            }
        };

        for(int i = 0; i < filenamesList.size(); ++i){
            routers.put(routersMacAddress.get(i), filenamesList.get(i));
        }


        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, arrayList);
        listWifiScan.setAdapter(adapter);

        //Instanciando o wifi manager
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        //Se o wifi estiver inativo, ativa-lo
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "WiFi está inativo... ativando ele agora!", Toast.LENGTH_LONG).show();
            wifiManager.setWifiEnabled(true);
        }

        //botão para alterar se será mostrados todas as redes ou apenas as cadastradas
        btnShowHide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (showAll) {
                    btnShowHide.setText("SHOW ALL NETWORKS");
                } else {
                    btnShowHide.setText("SHOW ONLY REGISTERED");
                }
                showAll = !showAll;
            }
        });

        //Botão de iniciar/parar
        btnStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Ao pedir para parar o escanemaneto:
                // mudar texto do botão, interromper repetição de escaneamentos, para timer.
                if(scanStarted){
                    btnStartStop.setText("START SCAN");
                    mHandler.removeCallbacks(mRunnable);
                    timer.onFinish();
                }
                //Ao pedir para iniciar o escanemaneto:
                else {
                    btnStartStop.setText("Stop Scan"); // mudar texto do botão.
                    fileSuffix = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss").format(Calendar.getInstance().getTime());
                    pointNumber = textPointNumber.getText().toString();
                    samples = 0; // iniciar contador de amostras.
                    textSampleCount.setText(String.valueOf(samples));
                    mHandler.post(mRunnable); // iniciar repetição de escaneamentos,
                }
                scanStarted = !scanStarted;

            }
        });

        getPermissions(context);

        //Definir ação do receiver ao receber resultados do escaneamento
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);

                //Se conseguiu receber os resultados do escaneamento, mostrar na tela os resultados
                if (success) {
                    scanResults = wifiManager.getScanResults();
                    if(!showAll){
                        samples +=1;
                        textSampleCount.setText(String.valueOf(samples));
                    }
                    for (ScanResult scanResult : scanResults) {
                        if(!showAll) {
                            if (routersMacAddress.contains(scanResult.BSSID)) {
                                arrayList.add("BSSID: " + scanResult.BSSID + " - " + scanResult.SSID + " - RSSI: " + scanResult.level + "dBm - " + scanResult.timestamp);
                                String filename = routers.get(scanResult.BSSID) + "_" + pointNumber + "_" + fileSuffix + ".txt";
                                String content = scanResult.level + ","+scanResult.timestamp+"\n";
                                //salvar resultado no arquivo
                                saveIntoFile( context,filename,content);
                            }
                        }
                        else{
                            arrayList.add("BSSID: " + scanResult.BSSID + " - " + scanResult.SSID + " - RSSI: " + scanResult.level + "dBm - " + scanResult.timestamp);
                        }
                    }
                    //limitar o numero de atualizações na lista
                    if(scanResults.get(0).timestamp - lastTimeStamp > (long) 10e6){
                        adapter.notifyDataSetChanged();
                        lastTimeStamp = scanResults.get(0).timestamp;
                    }



                } else {
                    // Mostrar que algo deu errado
                    Toast.makeText(getApplicationContext(), "Error receiving WiFi broadcast Sample n°:" + samples, Toast.LENGTH_SHORT).show();
                }
            }

        };

        setupScanWifi();//registrar pedido para receber scan wifi

        //Bloco de codigo para repetir periodicamente o escaneamento
        mRunnable = new Runnable() {
            @Override
            public void run() {

                scanWifi();
                timer.start();

                mHandler.postDelayed(mRunnable, timeInterval);
            }
        };

    }

    public void saveIntoFile(Context context,String filename, String content) {

        File file = new File(context.getFilesDir(), filename);
        String contentToSave = content.toLowerCase();

        try (FileOutputStream fos = context.openFileOutput(file.getName(), Context.MODE_APPEND)) {

            fos.write(contentToSave.getBytes());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupScanWifi(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        getApplicationContext().registerReceiver(wifiScanReceiver, intentFilter); //registrar pedido para receber escaneamento wifi
    }

    private void scanWifi() {
        arrayList.clear(); //limpar lista da tela

        boolean success = wifiManager.startScan(); // pedir para iniciar escaneamento

//        Toast.makeText(this, "Scanning WiFi ...", Toast.LENGTH_SHORT).show();

        //Se algo der errado mostrar Toast na tela
        if (!success) {
            Toast.makeText(this, "Error trying to start WiFi scan ...", Toast.LENGTH_SHORT).show();
        }

    }

    //Deve ter um jeito melhor de fazer isso
    void getPermissions(Context ctx){
        if (
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
                        &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED
                        &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        )
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_WIFI_STATE)
                    ||
                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_WIFI_STATE}, 1);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_WIFI_STATE}, 1);
            }
    }

    @Override
    protected void onPause() {
        super.onPause();
        btnStartStop.setText("Start Scan");
        mHandler.removeCallbacks(mRunnable);
        timer.onFinish();
        if(wifiScanReceiver.isOrderedBroadcast()) {
            unregisterReceiver(wifiScanReceiver);
        }

    }
    @Override
    protected void onResume() {
        super.onResume();
        setupScanWifi();
    }
}