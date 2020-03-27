package com.example.myapplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PresMeasurments {

    private final int BUFFER_SIZE = 5;

    List<Boolean> measurments;

    public PresMeasurments(){
        measurments = new ArrayList<>(Arrays.asList(false,false,false,false,false));
    }

    public void insertMeasure(boolean measure) {
        measurments.remove(0);
        measurments.add(measure);
    }

    public boolean getAvgMeasuere() {
        boolean res = false;
        for(boolean measure: measurments){
            res = res || measure;
        }
        return res;
    }

}
