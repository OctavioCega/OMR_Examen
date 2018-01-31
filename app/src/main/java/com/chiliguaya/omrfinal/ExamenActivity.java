package com.chiliguaya.omrfinal;

import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.utils.Converters;

import static org.opencv.core.CvType.CV_32F;
import static org.opencv.imgproc.Imgproc.moments;

import java.util.ArrayList;

import static org.opencv.core.CvType.CV_32FC2;

public class ExamenActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2,
        Toolbar.OnMenuItemClickListener {
    private static final String TAG = "OMR::ExamenActivity";

    private ExamenActivityView mOpenCvCameraView; // Responsable del preview

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV cargo bien");
                    mOpenCvCameraView.enableView();
                    //mOpenCvCameraView.setOnTouchListener(ExamenActivity.this); // quiero habilitar touch?
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private boolean flash = false;
    private boolean color = true;
    private int areaEficaz;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_examen);
        mOpenCvCameraView = (ExamenActivityView) findViewById(R.id.camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        //Ya que use un FrameLayout puedo poner este menu encima sin afectar la resolución del video
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("Quitar esta wea");
        toolbar.inflateMenu(R.menu.menu_examen);
        toolbar.setOnMenuItemClickListener(this);
    }


    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "No se encontro la biblioteca aqui, usando OpenCV Manager");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV encontrado dentro del pack. A usarlo!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public ExamenActivity() {
        Log.i(TAG, "Se instanció nuevo " + this.getClass());
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mProceso = new Mat();
        contornosAproximados2f = new MatOfPoint2f();
        contorno2f = new MatOfPoint2f();
        contour2fPadre = new MatOfPoint2f();
        contornoAproximado = new MatOfPoint();
        element1 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        areaEficaz = width * height;
        tiempoInicio = System.currentTimeMillis();

        transformada = new Mat(new Size(3,3),CV_32F);
        aux1 = new Mat();
        aux2 = new Mat();
        verticesMat = new Mat();
        puntosGet = new Mat();
        puntosDest = new Mat();
        marcas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            marcas.add(new Point(0, 0));
        }
        mRgba2 = new Mat();
    }

    @Override
    public void onCameraViewStopped() {

    }

    Mat mRgba;
    Mat mGray;
    Mat mProceso;
    Mat element1;
    Mat verticesMat;
    MatOfPoint2f contorno2f;
    MatOfPoint2f contour2fPadre;
    MatOfPoint contornoAproximado;
    MatOfPoint2f contornosAproximados2f;
    Moments mu;
    double min = 0, max = 1 / 10000;
    double tiempoInicio = 0;
    double tiempoActual = 0;
    int marcaA, marcaB, marcaC, contadorMarca, marcaD, marcaOrientacion = 0;
    int xA, xB, xC, yA, yB, yC, xD, yD;
    int marcaTR, marcaTL, marcaBL, marcaBR;//top right ,top lef, bot left
    double area;
    double perimetro;
    double fCContorno;
    boolean escaneo = false;
    double startTime;
    //https://en.wikipedia.org/wiki/Shape_factor_(image_analysis_and_microscopy)
    //https://stackoverflow.com/questions/45744567/opencv-hierarchy-is-always-null
    final double FC_CUADRADO = 4 * Math.PI / 16; //factor circularidad cuadrado
    final double FC_CIRCULO = 1;

    // debe ser igual a 0.7853981633974483 para un cuadrado perfecto

    //para trnasformar
    Mat puntosGet;
    Mat puntosDest;
    Mat transformada;
    Mat crop;
    Mat aux1;
    Mat aux2;
    ArrayList<Point> marcas;
    Mat mRgba2;
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        inputFrame.rgba().copyTo(mRgba2);
//        tiempoActual = System.currentTimeMillis();
//        if ((tiempoActual - tiempoInicio) < 40) {
        Imgproc.GaussianBlur(mGray, mProceso, new Size(5, 5), 3);
        //Tratar de implementar en un futuro https://www.pyimagesearch.com/2015/04/06/zero-parameter-automatic-canny-edge-detection-with-python-and-opencv/
        //https://stackoverflow.com/questions/22390131/java-and-opencv-calculate-median-mean-stdev-value-of-mat-gray-image
        Imgproc.Canny(mProceso, mProceso, 120, 150, 3, false);
        Imgproc.dilate(mProceso, mProceso, element1);
        Mat hierarchy = new Mat();
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(mProceso, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        //A procesar los contornos para encontrar los cuadritos
        contadorMarca = 0; //cuenta las 3 marcas tipo QR
        marcaOrientacion = 0; //cuenta la marca especial con triangulito
        int[] contornosMarcas = new int[contours.size()];
        for (int i = 0; i < contours.size(); i++) {
            area = Imgproc.contourArea(contours.get(i));
            contours.get(i).convertTo(contorno2f, CV_32FC2);
            perimetro = Imgproc.arcLength(contorno2f, true);
            fCContorno = 4 * Math.PI * area / Math.pow(perimetro, 2); //factor circularidad contorno actual
            double perimetroPadre = perimetro;
            double areaPadre = area;
            //Confimo si el contorno es un cuadrado, para filtrar ruidos
            if (fCContorno >= FC_CUADRADO * 0.8 && fCContorno <= FC_CUADRADO * 1.2 && area >= areaEficaz * 0.0005) {
                //en este punto ya tengo puros contornos de los cuadros, el problema es que tengo varios interno , externos,
                //debo ahora decir que solo me interesan los que tienen chamacos pero no padre, intento 1
                int[] jerarquia = new int[4]; //recuerda [next,previous,child,parent]
                hierarchy.get(0, i, jerarquia);
                //NIVEL 1
                if (jerarquia[2] != -1 && jerarquia[3] != -1) { //quiero que el contorno tenga hijos y que tenga padre
                    int contornoHijo = jerarquia[2];
                    hierarchy.get(0, contornoHijo, jerarquia); //ahora me enfoco en el hijo encontrado :O
                    //NIVEL 2
                    if (jerarquia[2] != -1 && jerarquia[3] == i) { //quiero que el contorno tenga hijos y que el padre sea el contorno actual
                        contornoHijo = jerarquia[2];
                        hierarchy.get(0, contornoHijo, jerarquia); //ahora me enfonoc en el hijo encontrado

                        //NIVEL 3 Ahora si definitivamente es la marca , espero... NO aun debo ir mas padentro
                        if (jerarquia[2] != -1) {
                            contornoHijo = jerarquia[2];
                            hierarchy.get(0, contornoHijo, jerarquia); //ahora me enfonoc en el hijo encontrado
                            //NIVEL 4, debería ser el ultimo, tampoco
                            if (jerarquia[2] != -1) {
                                contornoHijo = jerarquia[2];
                                hierarchy.get(0, contornoHijo, jerarquia); //ahora me enfonoc en el hijo encontrado
                                //NIVEL 5 .-.
                                if (jerarquia[2] == -1) {
                                    ArrayList<MatOfPoint> contornoHijo2 = new ArrayList<>();
                                    contornoHijo2.add(contours.get(contornoHijo)); //contorno aproximado
                                    Imgproc.fillPoly(mRgba, contornoHijo2, new Scalar(255, 0, 255));
                                    mu = moments(contours.get(i), true);
                                    contours.get(contornoHijo).convertTo(contorno2f, CV_32FC2);
                                    perimetro = Imgproc.arcLength(contorno2f, true);
                                    area = Imgproc.contourArea(contours.get(contornoHijo));
                                    fCContorno = 4 * Math.PI * area / Math.pow(perimetro, 2); //factor circularidad contorno actual
                                    if(area<(areaPadre*0.08)  &&marcaOrientacion ==0) {
                                        Imgproc.fillPoly(mRgba, contornoHijo2, new Scalar(0, 255, 0));
                                        xD = (int) (mu.get_m10() / mu.get_m00());
                                        yD = (int) (mu.get_m01() / mu.get_m00());
                                        marcaBR = i;
//                                        Imgproc.putText(mRgba, "p" + perimetro, new Point(xD,yD), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//                                        Imgproc.putText(mRgba, "apadre" + areaPadre, new Point(xD,yD+50), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//                                        Imgproc.putText(mRgba, "area" + area, new Point(xD,yD+100), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
                                        marcaOrientacion++;
                                    }
                                    if(area>(areaPadre*0.11)){ //sujeto a cambios
                                        if (contadorMarca == 0) {
                                            marcaA = i;
                                            xA = (int) (mu.get_m10() / mu.get_m00());
                                            yA = (int) (mu.get_m01() / mu.get_m00());
//                                            Imgproc.putText(mRgba, "p" + perimetro, new Point(xA,yA), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//                                            Imgproc.putText(mRgba, "apadre" + areaPadre, new Point(xA,yA+50), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//                                            Imgproc.putText(mRgba, "area" + area, new Point(xA,yA+100), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);

                                            //Imgproc.putText(mRgba, "A", new Point(xA + 20, yA), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
                                        } else if (contadorMarca == 1) {
                                            marcaB = i;
                                            xB = (int) (mu.get_m10() / mu.get_m00());
                                            yB = (int) (mu.get_m01() / mu.get_m00());
//                                            Imgproc.putText(mRgba, "p" + perimetro, new Point(xB,yB), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//                                            Imgproc.putText(mRgba, "apadre" + areaPadre, new Point(xB,yB+50), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//                                            Imgproc.putText(mRgba, "area" + area, new Point(xB,yB+100), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);

                                            //Imgproc.putText(mRgba, "B", new Point(xB + 20, yB), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
                                        } else if (contadorMarca == 2) {
                                            marcaC = i;
                                            xC = (int) (mu.get_m10() / mu.get_m00());
                                            yC = (int) (mu.get_m01() / mu.get_m00());
//                                            Imgproc.putText(mRgba, "p" + perimetro, new Point(xC,yC), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//                                            Imgproc.putText(mRgba, "apadre" + areaPadre, new Point(xC,yC  +50), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//                                            Imgproc.putText(mRgba, "area" + area, new Point(xC,yC+100), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);

                                            //Imgproc.putText(mRgba, "C", new Point(xC + 20, yC), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
                                        }
                                        contadorMarca++;
                                        if (contadorMarca == 3) {
                                            //Hay algun problema mejor no continuar
                                            //Hay mas de tres marcas , nope
                                            i = contours.size();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        //En este momento ya tengo las 3 marcas ahora tengo que ver cuales son
        //TOP LEFT, TOP RIGHT, BOT LEFT y calcular BOT RIGHT
        if (contadorMarca == 3 && marcaOrientacion ==1) {
            double distAB = Math.sqrt(Math.pow((xB - xA), 2) + Math.pow((yB - yA), 2));
            double distBC = Math.sqrt(Math.pow((xC - xB), 2) + Math.pow((yC - yB), 2));
            double distAC = Math.sqrt(Math.pow((xA - xC), 2) + Math.pow((yA - yC), 2));
            //ordeno las marcas :v
            if (distBC > distAB && distBC > distAC) {
                marcaTL = marcaA;
                if (distAB > distAC) {
                    marcaBL = marcaB;
                    marcaTR = marcaC;
                } else {
                    marcaBL = marcaC;
                    marcaTR = marcaB;
                }
            } else if (distAC > distAB && distAC > distBC) {
                marcaTL = marcaB;
                if (distAB > distBC) {
                    marcaBL = marcaA;
                    marcaTR = marcaC;
                } else {
                    marcaBL = marcaC;
                    marcaTR = marcaA;
                }
            } else if (distAB > distBC && distAB > distAC) {
                marcaTL = marcaC;
                if (distBC > distAC) {
                    marcaBL = marcaB;
                    marcaTR = marcaA;
                } else {
                    marcaBL = marcaA;
                    marcaTR = marcaB;
                }
            }
            //Ya se cual es cual ahora las marco por mientras y obtengo las coordenadas de sus centros
            mu = moments(contours.get(marcaTL), true);
            xA = (int) (mu.get_m10() / mu.get_m00());
            yA = (int) (mu.get_m01() / mu.get_m00());
            Imgproc.putText(mRgba, "TopIzq (A)", new Point(xA + 20, yA + 20), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
            mu = moments(contours.get(marcaBL), true);
            xB = (int) (mu.get_m10() / mu.get_m00());
            yB = (int) (mu.get_m01() / mu.get_m00());
            Imgproc.putText(mRgba, "BotIzq (B)", new Point(xB + 20, yB + 20), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
            mu = moments(contours.get(marcaTR), true);
            xC = (int) (mu.get_m10() / mu.get_m00());
            yC = (int) (mu.get_m01() / mu.get_m00());
            Imgproc.putText(mRgba, "TopDer (C)", new Point(xC + 20, yC + 20), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);

            //pinto todas los vertices en un orden
            ArrayList<MatOfPoint> contornoBL = new ArrayList<>();
            contornoBL.add(contours.get(marcaBL)); //contorno aproximado
            Imgproc.fillPoly(mRgba, contornoBL, new Scalar(255, 0, 0));
            ArrayList<MatOfPoint> contornoTL = new ArrayList<>();
            contornoTL.add(contours.get(marcaTL)); //contorno aproximado
            Imgproc.fillPoly(mRgba, contornoTL, new Scalar(0, 255, 0));
            ArrayList<MatOfPoint> contornoTR = new ArrayList<>();
            contornoTR.add(contours.get(marcaTR)); //contorno aproximado
            Imgproc.fillPoly(mRgba, contornoTR, new Scalar(0, 0, 255));
            ArrayList<MatOfPoint> contornoBR = new ArrayList<>();
            contornoBR.add(contours.get(marcaBR)); //contorno aproximado
            Imgproc.fillPoly(mRgba, contornoBR, new Scalar(255, 255, 0));
            Imgproc.line(mRgba, new Point(xA, yA), new Point(xC, yC), new Scalar(255, 0, 255), 5);
            Imgproc.line(mRgba, new Point(xB, yB), new Point(xD, yD), new Scalar(255, 0, 255), 5);
            Imgproc.line(mRgba, new Point(xC, yC), new Point(xD, yD), new Scalar(255, 0, 255), 5);
            Imgproc.line(mRgba, new Point(xA, yA), new Point(xB, yB), new Scalar(255, 0, 255), 5);
            marcas.set(0,new Point(xB,yB));
            marcas.set(1,new Point(xA,yA));
            marcas.set(2,new Point(xC,yC));
            marcas.set(3,new Point(xD,yD));
//           ahora puedo extraer el examen al fin
            //calculo distancia entre las coordenadas orriginales del examen para poder trandformarla a escala
            double dist1 = Math.sqrt(Math.pow((xC - xD), 2) + Math.pow((yC - yD), 2));
            double dist2 = Math.sqrt(Math.pow((xD - xB), 2) + Math.pow((yD - yD), 2));
            double dist3 = Math.sqrt(Math.pow((xC - xA), 2) + Math.pow((yC - yA), 2));
            double dist4 = (dist2 + dist3) / 2;
            double escala = mRgba.width() / dist1;

            //Quiero que escanee solo cada 2.7 segundos, un delay (al final)
            if (true) {
                startTime = System.currentTimeMillis();
                escaneo = true;
                //ring.start(); //debo agregar opcion de sonido activado o no
                //Log.d(TAG,"Encontre examen, trabajando... " + startTime);
                ArrayList<Point> puntosDestino = new ArrayList();
                //con esto obligo a que la imagen escale hasta lo mas largo y luego proporcionalmente lo ancho :D 2018-01-07
                puntosDestino.add(new Point(0, mRgba.height())); // bot left ->0
                puntosDestino.add(new Point(0, 0)); ///top left                 ->1
                puntosDestino.add(new Point(mRgba.width(), 0)); //top right                                 ->2
                puntosDestino.add(new Point(mRgba.width(), mRgba.height())); //bot right            ->3
                puntosGet = Converters.vector_Point2d_to_Mat(marcas);
                puntosDest = Converters.vector_Point2d_to_Mat(puntosDestino);
                puntosGet.convertTo(aux1, CV_32F);
                puntosDest.convertTo(aux2, CV_32F);
                transformada = Imgproc.getPerspectiveTransform(aux1, aux2);
                Imgproc.warpPerspective(mRgba, mRgba2 , transformada, mRgba.size());
                return (mRgba2);
            }
        }
//        }
//        else {
//            if (tiempoActual - tiempoInicio > 950)
//                tiempoInicio = System.currentTimeMillis();
//        }
        if (color) {
            return mRgba;
        } else {
            return mProceso;
        }
    }

    //Control del menu. Gracias a respuesta pregunta Toolbar
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.opcion1:
                mostrarConfig();
                break;
            case R.id.flash:
                if (!flash) {
                    mOpenCvCameraView.setFlashOn(true);
                    flash = true;
                } else {
                    mOpenCvCameraView.setFlashOn(false);
                    flash = false;
                }
            default:
        }
        return false;
    }

    public void mostrarConfig() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ExamenActivity.this);
        View mView = getLayoutInflater().inflate(R.layout.dialog_config, null);
        SeekBar seekbar1 = mView.findViewById(R.id.seekBar);
        SeekBar seekbar2 = mView.findViewById(R.id.seekBar2);
        seekbar2.setProgress((int) max);
        final TextView textView =  mView.findViewById(R.id.textView2);
        final TextView textView2 = mView.findViewById(R.id.textView3);
        textView2.setText("" + max);
        final CheckBox checkBox = mView.findViewById(R.id.checkBox);
        Button button = mView.findViewById(R.id.button2);
        builder.setView(mView);
        final AlertDialog dialog = builder.create();
        dialog.show();
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        seekbar1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                min = i;
                textView.setText("" + i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        seekbar2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                max = i;
                textView2.setText("" + i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                color = checkBox.isChecked();
            }
        });
    }

}
