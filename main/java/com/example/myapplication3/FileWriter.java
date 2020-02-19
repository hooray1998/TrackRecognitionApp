package com.example.myapplication3;

import android.content.Context;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileWriter {

    private Context mContext;
    FileOutputStream file;

    public FileWriter() {
    }

    public FileWriter(Context mContext) {
        super();
        this.mContext = mContext;
    }

    /*
     * 这里定义的是一个文件保存的方法，写入到文件中，所以是输出流
     * */
    public void create(String filename) {

        String curFilename = filename + "_" + System.currentTimeMillis() + ".txt";

        try {
            file = mContext.openFileOutput(curFilename, Context.MODE_APPEND);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    public void append(String line, boolean recoding) {
        if (!recoding) return;
        line = System.currentTimeMillis() + "," + line + "\n";
        try {
            file.write(line.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void append(float[] values, boolean recoding) {
        if (!recoding) return;

        String line = System.currentTimeMillis() + "," + join(values) + "\n";
        try {
            file.write(line.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close(){
        try {
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String join(float[] elements) {
        String ret = "";
        int size = elements.length;
        for (int i = 0; i < size; i++) {
            if (i < size - 1)
                ret += elements[i] + ",";
            else {
                ret += elements[i];
            }
        }
        return ret;
    }

}
