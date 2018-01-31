package com.chiliguaya.omrfinal;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private final String TAG = new String ("OMR::MainActivity");
    private final int MY_PERMISSIONS_REQUEST_CAMERA = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button btnCalificar = (Button) findViewById(R.id.btnCalificar);
        btnCalificar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Here, thisActivity is the current activity
                if(permisoCamera()) {
                    Log.d(TAG, "Abrir examenActivity");
                    Intent intent = new Intent(MainActivity.this, ExamenActivity.class);
                    startActivity(intent);
                }
            }
        });
    }


    private boolean permisoCamera(){
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.CAMERA)) {
                Log.d(TAG,"Informando del permiso");
                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // Envio una alertdialog explicando que reconsidere aceptar la camara
                mostrarMensajeOkNo("Es necesarip que aceptes el permiso de cámara para poder capturar los examenes");

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{android.Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);
            }
            return false;
        }
        else{
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG,"Permiso aceptado");
                    Log.d(TAG,"Abrir examenActivity (desde onRequest)");
                    Intent intent = new Intent(MainActivity.this, ExamenActivity.class);
                    startActivity(intent);
                    // permission was granted, yay! Do the
                    // camera-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.d(TAG,"Permiso rechazado");

                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    private void mostrarMensajeOkNo(String message) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("Ok", dialogListener)
                .setNegativeButton("Cancelar", dialogListener)
                .create()
                .show();
    }

    DialogInterface.OnClickListener dialogListener = new DialogInterface.OnClickListener() {

        final int BUTTON_CANCELAR = -2;
        final int BUTTON_OK = -1;

        @Override
        public void onClick(DialogInterface dialog, int cual) {
            switch (cual) {
                case BUTTON_CANCELAR:
                    Log.d(TAG, "Solicitud de permiso rechazada");
                    dialog.dismiss();
                    break;

                case BUTTON_OK:
                    //Vuelvo a pregunar sobre el permiso
                    Log.d(TAG, "Volviendo a solicitar permiso");
                    ActivityCompat.requestPermissions(
                            MainActivity.this, new String[]{android.Manifest.permission.CAMERA},
                            MY_PERMISSIONS_REQUEST_CAMERA);
                    dialog.dismiss();
                    break;
            }
        }
    };
}
