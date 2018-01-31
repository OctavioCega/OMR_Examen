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
    //https://en.wikipedia.org/wiki/Shape_factor_(image_analysis_and_microscopy)
    //https://stackoverflow.com/questions/45744567/opencv-hierarchy-is-always-null
    final double FC_CUADRADO = 4 * Math.PI / 16; //factor circularidad cuadrado
    final double FC_CIRCULO = 1;

    // debe ser igual a 0.7853981633974483 para un cuadrado perfecto
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

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
        for (int i = 0; i < contours.size(); i++) {
            area = Imgproc.contourArea(contours.get(i));
            contours.get(i).convertTo(contorno2f, CV_32FC2);
            perimetro = Imgproc.arcLength(contorno2f, true);
            fCContorno = 4 * Math.PI * area / Math.pow(perimetro, 2); //factor circularidad contorno actual
            double fCContornoPadre = fCContorno;

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
                                    if(fCContorno<0.8 &&marcaOrientacion ==0) {
                                        Imgproc.fillPoly(mRgba, contornoHijo2, new Scalar(0, 255, 0));
                                        xD = (int) (mu.get_m10() / mu.get_m00());
                                        yD = (int) (mu.get_m01() / mu.get_m00());
                                        Imgproc.putText(mRgba, "p" + perimetro, new Point(xD,yD), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);

                                        marcaOrientacion++;
                                    }
                                    else {
                                        if (contadorMarca == 0) {
                                            marcaA = i;
                                            xA = (int) (mu.get_m10() / mu.get_m00());
                                            yA = (int) (mu.get_m01() / mu.get_m00());
                                            Imgproc.putText(mRgba, "p" + perimetro, new Point(xA,yA), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);

                                            //Imgproc.putText(mRgba, "A", new Point(xA + 20, yA), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
                                        } else if (contadorMarca == 1) {
                                            marcaB = i;
                                            xB = (int) (mu.get_m10() / mu.get_m00());
                                            yB = (int) (mu.get_m01() / mu.get_m00());
                                            Imgproc.putText(mRgba, "p" + perimetro, new Point(xB,yB), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);

                                            //Imgproc.putText(mRgba, "B", new Point(xB + 20, yB), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
                                        } else if (contadorMarca == 2) {
                                            marcaC = i;
                                            xC = (int) (mu.get_m10() / mu.get_m00());
                                            yC = (int) (mu.get_m01() / mu.get_m00());
                                            Imgproc.putText(mRgba, "p" + perimetro, new Point(xC,yC), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);

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
            Imgproc.drawContours(mRgba, contours, marcaTL, new Scalar(0, 255, 0), 2);
            mu = moments(contours.get(marcaBL), true);
            xB = (int) (mu.get_m10() / mu.get_m00());
            yB = (int) (mu.get_m01() / mu.get_m00());
            Imgproc.putText(mRgba, "BotIzq (B)", new Point(xB + 20, yB + 20), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
            mu = moments(contours.get(marcaTR), true);
            xC = (int) (mu.get_m10() / mu.get_m00());
            yC = (int) (mu.get_m01() / mu.get_m00());
            Imgproc.putText(mRgba, "TopDer (C)", new Point(xC + 20, yC + 20), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);

//            //A crear la linea, primero necesito las esquinas del contorno so usare approxpoly en los dos primero.
//            int[] esquinasUtilesBL = {0,0};
//            int[] esquinasUtilesTR = {0,0};
//            double PORCENTAJE = max/10000;
//            ArrayList<MatOfPoint> contornosAproximadosList = new ArrayList<>();
//            for(int i = 0; i<2; i++){
//                contornosAproximadosList.add(new MatOfPoint());
//            }
//            contours.get(marcaBL).convertTo(contorno2f, CV_32FC2);
//            double perimetroBL = Imgproc.arcLength(contorno2f, true);
//            Imgproc.approxPolyDP(contorno2f, contornosAproximados2f, perimetroBL*PORCENTAJE, true); //a partir de 5 ya detecta los lados YAY!!
//            contornosAproximados2f.convertTo(contornoAproximado, CV_32S);
//            contornosAproximadosList.add(0,contornoAproximado); //index 0 contornoBL
//            contours.get(marcaTR).convertTo(contorno2f, CV_32FC2);
//            verticesMat = contornosAproximadosList.get(0);
//            Imgproc.putText(mRgba, "VBL " + verticesMat.rows(), new Point(600 , 50), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//            Imgproc.drawContours(mRgba, contornosAproximadosList, 0, new Scalar(255, 255, 0), 2);
//            double[] distanciasEsquinasBR = new double[verticesMat.rows()];
//            double[] esquina;
//            for(int i = 0; i<verticesMat.rows();i++){
//                esquina = verticesMat.get(i, 0);
//                distanciasEsquinasBR[i] = Math.sqrt(Math.pow((esquina[0]- xA), 2) + Math.pow((esquina[1] - yA), 2));
//            }
//            //ya puedo saber cual esquina de la marca pertene a la basura, digo a la parte inferior del cuadrito :3 chido
//            //tengo que descubrir cuales son
//            double distMasLarga =  distanciasEsquinasBR[0];
//            for(int i = 0;i<distanciasEsquinasBR.length;i++){
//                if(distanciasEsquinasBR[i]>distMasLarga){
//                    distMasLarga = distanciasEsquinasBR[i];
//                }
//            }
//            //ya se cual debe ser la distancia más larga así que debo comparar nada más
//            int j = 0;
//            double[] esquinaNoRepetir = {0,0};
//            double[] esquinaActual ={0,0};
//            for(int i = 0;i<distanciasEsquinasBR.length;i++){
//                if(distanciasEsquinasBR[i]>=distMasLarga-10){
//                    esquinaActual = verticesMat.get(i,0);
//                    if(j==0){
//                        esquinasUtilesBL[0] = i;
//                        esquinaNoRepetir = verticesMat.get(i,0);
//                        j++;
//                    }
//                    else if(j==1 ){
//                        esquinasUtilesBL[1] = i;
//                        Log.d(TAG,"[0]BL EsquinaNORepetir " + "x" + esquinaNoRepetir[0] + " y" + esquinaNoRepetir[1]);
//                        Log.d(TAG,"[1]BL Esquinaactual  " + "x" + esquinaActual[0] + " y" + esquinaActual[1]);
//                        if(esquinaNoRepetir[0] ==esquinaActual[0] ||esquinaNoRepetir[1] ==esquinaActual[1] ){
//                            Log.d(TAG,"CUIDADO");
//
//                        }
//                        j++;
//                    }
//                }
//            }
//            double x1;
//            double y1;
//            double x2;
//            double y2;
//            //ya se cual esquina es cual y tengo las dos importantes vamos a dibujarlas
//            double[] cornerBL = verticesMat.get(esquinasUtilesBL[0], 0);
//            Imgproc.putText(mRgba, "0", new Point(cornerBL[0],cornerBL[1]), Core.FONT_ITALIC, 1, new Scalar(255, 0, 0), 3);
//            x1 = cornerBL[0];
//            y1 = cornerBL[1];
//            cornerBL = verticesMat.get(esquinasUtilesBL[1], 0);
//            Imgproc.putText(mRgba, "1", new Point(cornerBL[0],cornerBL[1]), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//            x2 = cornerBL[0];
//            y2 = cornerBL[1];
//            //ahora vamos con TR
//            double perimetroTR = Imgproc.arcLength(contorno2f, true);
//            Imgproc.approxPolyDP(contorno2f, contornosAproximados2f, perimetroTR*PORCENTAJE, true); //a partir de 5 ya detecta los lados YAY!!
//            contornosAproximados2f.convertTo(contornoAproximado, CV_32S);
//            contornosAproximadosList.add(1,contornoAproximado);
//            verticesMat = contornosAproximadosList.get(1);
//            Imgproc.putText(mRgba, "VTR " + verticesMat.rows(), new Point(600 , 100), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//            Imgproc.putText(mRgba, "e " + max, new Point(600 , 150), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//            Imgproc.drawContours(mRgba, contornosAproximadosList, 1, new Scalar(255, 255, 0), 2);
//            double[] distanciasEsquinasTR = new double[verticesMat.rows()];
//            for(int i = 0; i<verticesMat.rows();i++){
//                esquina = verticesMat.get(i, 0);
//                distanciasEsquinasTR[i] = Math.sqrt(Math.pow((esquina[0]- xA), 2) + Math.pow((esquina[1] - yA), 2));
//            }
//            //ya puedo saber cual esquina de la marca pertene a la basura, digo a la parte derecha del cuadrito :3 chido
//            //tengo que descubrir cuales son
//            distMasLarga =  distanciasEsquinasTR[0];
//            for(int i = 0;i<distanciasEsquinasTR.length;i++){
//                if(distanciasEsquinasTR[i]>distMasLarga){
//                    distMasLarga = distanciasEsquinasTR[i];
//                }
//            }
//            //ya se cual debe ser la distancia más larga así que debo comparar nada más
//            j = 0;
//            for(int i = 0;i<distanciasEsquinasTR.length;i++){
//                esquinaActual = verticesMat.get(i,0);
//                if(distanciasEsquinasTR[i]>=distMasLarga-10){
//                    if(j==0){
//                        esquinaNoRepetir = esquinaActual;
//                        esquinasUtilesTR[0] = i;
//                        j++;
//                    }
//                    else if(j==1 ){
//                        esquinasUtilesTR[1] = i;
//                        Log.d(TAG,"[0]TR EsquinaNORepetir " + "x" + esquinaNoRepetir[0] + " y" + esquinaNoRepetir[1]);
//                        Log.d(TAG,"[1]TR Esquinaactual  " + "x" + esquinaActual[0] + " y" + esquinaActual[1]);
//                        if(esquinaNoRepetir[0] ==esquinaActual[0] ||esquinaNoRepetir[1] ==esquinaActual[1] ){
//                            Log.d(TAG,"CUIDADO TR");
//
//                        }
//                        j++;
//                    }
//                }
//            }
//            //ya se cual esquina es cual y tengo las dos importantes vamos a dibujarlas
//            double[] cornerTR = verticesMat.get(esquinasUtilesTR[0], 0);
//            Imgproc.putText(mRgba, "0", new Point(cornerTR[0],cornerTR[1]), Core.FONT_ITALIC, 1, new Scalar(255, 0, 0), 3);
//            double x3 = cornerTR[0];
//            double y3 = cornerTR[1];
//            cornerTR = verticesMat.get(esquinasUtilesTR[1], 0);
//            Imgproc.putText(mRgba, "1", new Point(cornerTR[0],cornerTR[1]), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//            double x4 = cornerTR[0];
//            double y4 = cornerTR[1];
//            //Ya tengo las dos posibles coordenadas para lanzar la línea, vamo a darle pues
//            //A calcular la interseccion de dos lineas con esta formula
//            //https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection //no funciono por algun motivo ...
//            //considero aqui a cornerBL x1y1,x2y2 y cornerTR x3,y3,x4,
//            double denominador = (x2-x1)*(y4-y3)-(x4-x3)*(y2-y1);
//            double xD = (x2*y1-x1*y2)*(x4-x3)-(x2-x1)*(x4*y3-x3*y4);
//            double yD = (x2*y1-x1*y2)*(y4-y3)-(y2-y1)*(x4*y4-x3*y4);
//            if(denominador!=0) {
//                xD = xD / denominador;
//                yD =yD / denominador;
//                if(xD>0 &&yD>0) {
//                    Imgproc.line(mRgba, new Point(xD, yD), new Point(x1, y1), new Scalar(0, 255, 255), 2);
//                    Imgproc.line(mRgba, new Point(xD, yD), new Point(x3, y3), new Scalar(0, 255, 255), 2);
//                    Imgproc.putText(mRgba, "xD " + xD + " yD " + yD , new Point(xD,yD), Core.FONT_ITALIC, 1, new Scalar(0, 0, 255), 3);
//
//                }
////                Log.d(TAG, "X1,Y1=" + x1+","+y1);
////                Log.d(TAG, "X2,Y2=" + x2+","+y2);
////                Log.d(TAG, "X3,Y3=" + x3+","+y3);
////                Log.d(TAG, "X4,Y4=" + x4+","+y4);
////                Log.d(TAG, "XD,YD=" + xD+","+yD);
//
//
//            }
//            else{
////                Log.d(TAG,"Denominador zero :(");
////                Log.d(TAG, "X1,Y1=" + x1+","+y1);
////                Log.d(TAG, "X2,Y2=" + x2+","+y2);
////                Log.d(TAG, "X3,Y3=" + x3+","+y3);
////                Log.d(TAG, "X4,Y4=" + x4+","+y4);
//            }
            Imgproc.line(mRgba, new Point(xA, yA), new Point(xC, yC), new Scalar(255, 0, 0), 2);
            Imgproc.line(mRgba, new Point(xB, yB), new Point(xD, yD), new Scalar(0, 255, 0), 2);
            Imgproc.line(mRgba, new Point(xC, yC), new Point(xD, yD), new Scalar(0, 0, 255), 2);
            Imgproc.line(mRgba, new Point(xA, yA), new Point(xB, yB), new Scalar(0, 255, 255), 2);

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
