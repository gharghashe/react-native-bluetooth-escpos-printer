
package cn.jystudio.bluetooth.escpos;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;

import cn.jystudio.bluetooth.BluetoothService;
import cn.jystudio.bluetooth.BluetoothServiceStateObserver;
import cn.jystudio.bluetooth.escpos.command.sdk.Command;
import cn.jystudio.bluetooth.escpos.command.sdk.PrintPicture;
import cn.jystudio.bluetooth.escpos.command.sdk.PrinterCommand;
import com.facebook.react.bridge.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.util.*;
import java.io.ByteArrayOutputStream;

public class RNBluetoothEscposPrinterModule extends ReactContextBaseJavaModule
        implements BluetoothServiceStateObserver {
    private static final String TAG = "BluetoothEscposPrinter";

    public static final int WIDTH_58 = 384;
    public static final int WIDTH_80 = 576;
    private final ReactApplicationContext reactContext;
    /******************************************************************************************************/

    private int deviceWidth = WIDTH_58;
    private BluetoothService mService;

    private static final int DITHERING_NO = 0;
    private static final int DITHERING_RANDOM_THRESHOLD = 1;
    private static final int DITHERING_FLOYD_STEINBERG = 2;
    private static final byte CAN = 24;
    private static final byte ESC = 27;
    private static final byte GS = 29;
    private static final byte[] cmd_ESCFF = new byte[]{27, 12};
    private static final int MAX_RLE_LENGTH = 62;
    private static double[] threshold = new double[]{0.25D, 0.26D, 0.27D, 0.28D, 0.29D, 0.3D, 0.31D, 0.32D, 0.33D, 0.34D, 0.35D, 0.36D, 0.37D, 0.38D, 0.39D, 0.4D, 0.41D, 0.42D, 0.43D, 0.44D, 0.45D, 0.46D, 0.47D, 0.48D, 0.49D, 0.5D, 0.51D, 0.52D, 0.53D, 0.54D, 0.55D, 0.56D, 0.57D, 0.58D, 0.59D, 0.6D, 0.61D, 0.62D, 0.63D, 0.64D, 0.65D, 0.66D, 0.67D, 0.68D, 0.69D};


    public RNBluetoothEscposPrinterModule(ReactApplicationContext reactContext,
                                          BluetoothService bluetoothService) {
        super(reactContext);
        this.reactContext = reactContext;
        this.mService = bluetoothService;
        this.mService.addStateObserver(this);
    }

    @Override
    public String getName() {
        return "BluetoothEscposPrinter";
    }


    @Override
    public
    @Nullable
    Map<String, Object> getConstants() {
        Map<String, Object> constants = new HashMap<>();
        constants.put("width58", WIDTH_58);
        constants.put("width80", WIDTH_80);
        return constants;
    }

    @ReactMethod
    public void printerInit(final Promise promise){
        if(sendDataByte(PrinterCommand.POS_Set_PrtInit())){
            promise.resolve(null);
        }else{
            promise.reject("COMMAND_NOT_SEND");
        }
    }

    @ReactMethod
    public void printAndFeed(int feed,final Promise promise){
        if(sendDataByte(PrinterCommand.POS_Set_PrtAndFeedPaper(feed))){
            promise.resolve(null);
        }else{
            promise.reject("COMMAND_NOT_SEND");
        }
    }

    @ReactMethod
    public void printerLeftSpace(int sp,final Promise promise){
        if(sendDataByte(PrinterCommand.POS_Set_LeftSP(sp))){
            promise.resolve(null);
        }else{
            promise.reject("COMMAND_NOT_SEND");
        }
    }

    @ReactMethod
    public void printerLineSpace(int sp,final Promise promise){
        byte[] command = PrinterCommand.POS_Set_DefLineSpace();
        if(sp>0){
            command = PrinterCommand.POS_Set_LineSpace(sp);
        }
        if(command==null || !sendDataByte(command)){
            promise.reject("COMMAND_NOT_SEND");
        }else{
            promise.resolve(null);
        }
    }

    /**
     * Under line switch, 0-off,1-on,2-deeper
     * @param line 0-off,1-on,2-deeper
     */
    @ReactMethod
    public void printerUnderLine(int line,final Promise promise){
        if(sendDataByte(PrinterCommand.POS_Set_UnderLine(line))){
            promise.resolve(null);
        }else{
            promise.reject("COMMAND_NOT_SEND");
        }
    }

    /**
     * When n=0 or 48, left justification is enabled
     * When n=1 or 49, center justification is enabled
     * When n=2 or 50, right justification is enabled
     * @param align
     * @param promise
     */
    @ReactMethod
    public void printerAlign(int align,final Promise promise){
        Log.d(TAG,"Align:"+align);
        if(sendDataByte(PrinterCommand.POS_S_Align(align))){
            promise.resolve(null);
        }else{
            promise.reject("COMMAND_NOT_SEND");
        }
    }


    @ReactMethod
    public void printText(String text, @Nullable  ReadableMap options, final Promise promise) {
        try {
            String encoding = "GBK";
            int codepage = 0;
            int widthTimes = 0;
            int heigthTimes=0;
            int fonttype=0;
            if(options!=null) {
                encoding = options.hasKey("encoding") ? options.getString("encoding") : "GBK";
                codepage = options.hasKey("codepage") ? options.getInt("codepage") : 0;
                widthTimes = options.hasKey("widthtimes") ? options.getInt("widthtimes") : 0;
                heigthTimes = options.hasKey("heigthtimes") ? options.getInt("heigthtimes") : 0;
                fonttype = options.hasKey("fonttype") ? options.getInt("fonttype") : 0;
            }
            String toPrint = text;
//            if ("UTF-8".equalsIgnoreCase(encoding)) {
//                byte[] b = text.getBytes("UTF-8");
//                toPrint = new String(b, Charset.forName(encoding));
//            }

            byte[] bytes = PrinterCommand.POS_Print_Text(toPrint, encoding, codepage, widthTimes, heigthTimes, fonttype);
            if (sendDataByte(bytes)) {
                promise.resolve(null);
            } else {
                promise.reject("COMMAND_NOT_SEND");
            }
        }catch (Exception e){
            promise.reject(e.getMessage(),e);
        }
    }

    @ReactMethod
    public void printColumn(ReadableArray columnWidths,ReadableArray columnAligns,ReadableArray columnTexts,
                            @Nullable ReadableMap options,final Promise promise){
        if(columnWidths.size()!=columnTexts.size() || columnWidths.size()!=columnAligns.size()){
            promise.reject("COLUMN_WIDTHS_ALIGNS_AND_TEXTS_NOT_MATCH");
            return;
        }
            int totalLen = 0;
            for(int i=0;i<columnWidths.size();i++){
                totalLen+=columnWidths.getInt(i);
            }
            int maxLen = deviceWidth/8;
            if(totalLen>maxLen){
                promise.reject("COLUNM_WIDTHS_TOO_LARGE");
                return;
            }

        String encoding = "GBK";
        int codepage = 0;
        int widthTimes = 0;
        int heigthTimes = 0;
        int fonttype = 0;
        if (options != null) {
            encoding = options.hasKey("encoding") ? options.getString("encoding") : "GBK";
            codepage = options.hasKey("codepage") ? options.getInt("codepage") : 0;
            widthTimes = options.hasKey("widthtimes") ? options.getInt("widthtimes") : 0;
            heigthTimes = options.hasKey("heigthtimes") ? options.getInt("heigthtimes") : 0;
            fonttype = options.hasKey("fonttype") ? options.getInt("fonttype") : 0;
        }
        Log.d(TAG,"encoding: "+encoding);

        /**
         * [column1-1,
         * column1-2,
         * column1-3 ... column1-n]
         * ,
         *  [column2-1,
         * column2-2,
         * column2-3 ... column2-n]
         *
         * ...
         *
         */
        List<List<String>> table = new ArrayList<List<String>>();

        /**splits the column text to few rows and applies the alignment **/
        int padding = 1;
        for(int i=0;i<columnWidths.size();i++){
            int width =columnWidths.getInt(i)-padding;//1 char padding
            String text = String.copyValueOf(columnTexts.getString(i).toCharArray());
            List<ColumnSplitedString> splited = new ArrayList<ColumnSplitedString>();
            int shorter = 0;
            int counter = 0;
            String temp = "";
            for(int c=0;c<text.length();c++){
                char ch = text.charAt(c);
                int l = isChinese(ch)?2:1;
                if (l==2){
                    shorter++;
                }
                temp=temp+ch;

                if(counter+l<width){
                   counter = counter+l;
                }else{
                    splited.add(new ColumnSplitedString(shorter,temp));
                    temp = "";
                    counter=0;
                    shorter=0;
                }
            }
            if(temp.length()>0) {
                splited.add(new ColumnSplitedString(shorter,temp));
            }
            int align = columnAligns.getInt(i);

            List<String> formated = new ArrayList<String>();
            for(ColumnSplitedString s: splited){
                StringBuilder empty = new StringBuilder();
                for(int w=0;w<(width+padding-s.getShorter());w++){
                    empty.append(" ");
                }
                int startIdx = 0;
                String ss = s.getStr();
                if(align == 1 && ss.length()<(width-s.getShorter())){
                    startIdx = (width-s.getShorter()-ss.length())/2;
                    if(startIdx+ss.length()>width-s.getShorter()){
                        startIdx--;
                    }
                    if(startIdx<0){
                        startIdx=0;
                    }
                }else if(align==2 && ss.length()<(width-s.getShorter())){
                    startIdx =width - s.getShorter()-ss.length();
                }
                Log.d(TAG,"empty.replace("+startIdx+","+(startIdx+ss.length())+","+ss+")");
                empty.replace(startIdx,startIdx+ss.length(),ss);
                formated.add(empty.toString());
            }
            table.add(formated);

        }

        /**  try to find the max row count of the table **/
        int maxRowCount = 0;
        for(int i=0;i<table.size()/*column count*/;i++){
            List<String> rows = table.get(i); // row data in current column
            if(rows.size()>maxRowCount){maxRowCount = rows.size();}// try to find the max row count;
        }

        /** loop table again to fill the rows **/
        StringBuilder[] rowsToPrint = new StringBuilder[maxRowCount];
        for(int column=0;column<table.size()/*column count*/;column++){
            List<String> rows = table.get(column); // row data in current column
            for(int row=0;row<maxRowCount;row++){
                if(rowsToPrint[row]==null){
                    rowsToPrint[row] = new StringBuilder();
                }
                if(row<rows.size()){
                    //got the row of this column
                    rowsToPrint[row].append(rows.get(row));
                }else{
                    int w =columnWidths.getInt(column);
                    StringBuilder empty = new StringBuilder();
                   for(int i=0;i<w;i++){
                       empty.append(" ");
                   }
                    rowsToPrint[row].append(empty.toString());//Append spaces to ensure the format
                }
            }
        }

        /** loops the rows and print **/
        for(int i=0;i<rowsToPrint.length;i++){
            rowsToPrint[i].append("\n\r");//wrap line..
            try {
//                byte[] toPrint = rowsToPrint[i].toString().getBytes("UTF-8");
//                String text = new String(toPrint, Charset.forName(encoding));
                if (!sendDataByte(PrinterCommand.POS_Print_Text(rowsToPrint[i].toString(), encoding, codepage, widthTimes, heigthTimes, fonttype))) {
                    promise.reject("COMMAND_NOT_SEND");
                    return;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        promise.resolve(null);
    }

    @ReactMethod
    public void setWidth(int width) {
        deviceWidth = width;
    }

    @ReactMethod
    public void printPic(String base64encodeStr, @Nullable  ReadableMap options) {
        int width = 0;
        int leftPadding = 0;
        String type = "other";
        if(options!=null){
            width = options.hasKey("width") ? options.getInt("width") : 0;
            leftPadding = options.hasKey("left")?options.getInt("left") : 0;
            type = options.hasKey("type")?options.getString("type") : "other";
        }

        //cannot larger then devicesWith;
        //if(width > deviceWidth || width == 0){
        //    width = deviceWidth;
        //}

        byte[] bytes = Base64.decode(base64encodeStr, Base64.DEFAULT);
        Bitmap mBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        int nMode = 0;
        if (mBitmap != null) {
            /**
             * Parameters:
             * mBitmap  要打印的图片
             * nWidth   打印宽度（58和80）
             * nMode    打印模式
             * Returns: byte[]
             */

            if(type.equals("woosim")){
                byte[] data = fastPrintBitmap(0, 0, width, mBitmap.getHeight(), mBitmap);
                mBitmap.recycle();

                sendDataByte(new byte[]{27, 76});
                sendDataByte(data);
                sendDataByte(new byte[]{27, 83});
            }
            else{
                byte[] data = PrintPicture.POS_PrintBMP(mBitmap, width, nMode, leftPadding);
                //	SendDataByte(buffer);
                sendDataByte(Command.ESC_Init);
                sendDataByte(Command.LF);
                sendDataByte(data);
                sendDataByte(PrinterCommand.POS_Set_PrtAndFeedPaper(30));
                sendDataByte(PrinterCommand.POS_Set_Cut(1));
                sendDataByte(PrinterCommand.POS_Set_PrtInit());
            }
        }
    }


    @ReactMethod
    public void selfTest(@Nullable Callback cb) {
        boolean result = sendDataByte(PrinterCommand.POS_Set_PrtSelfTest());
        if (cb != null) {
            cb.invoke(result);
        }
    }

    /**
     * Rotate 90 degree, 0-no rotate, 1-rotate
     * @param rotate  0-no rotate, 1-rotate
     */
    @ReactMethod
    public void rotate(int rotate,final Promise promise) {
        if(sendDataByte(PrinterCommand.POS_Set_Rotate(rotate))){
            promise.resolve(null);
        }else{
            promise.reject("COMMAND_NOT_SEND");
        }
    }

    @ReactMethod
    public void setBlob(int weight,final Promise promise) {
        if(sendDataByte(PrinterCommand.POS_Set_Bold(weight))){
            promise.resolve(null);
        }else{
            promise.reject("COMMAND_NOT_SEND");
        }
    }

    @ReactMethod
    public void printQRCode(String content, int size, int correctionLevel, final Promise promise) {
        try {
            Log.i(TAG, "生成的文本：" + content);
            // 把输入的文本转为二维码
            Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.forBits(correctionLevel));
            BitMatrix bitMatrix = new QRCodeWriter().encode(content,
                    BarcodeFormat.QR_CODE, size, size, hints);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();

            System.out.println("w:" + width + "h:"
                    + height);

            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * width + x] = 0xff000000;
                    } else {
                        pixels[y * width + x] = 0xffffffff;
                    }
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height,
                    Bitmap.Config.ARGB_8888);

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            //TODO: may need a left padding to align center.
            byte[] data = PrintPicture.POS_PrintBMP(bitmap, size, 0, 0);
            if (sendDataByte(data)) {
                promise.resolve(null);
            } else {
                promise.reject("COMMAND_NOT_SEND");
            }
        } catch (Exception e) {
            promise.reject(e.getMessage(), e);
        }
    }

    @ReactMethod
    public void printBarCode(String str, int nType, int nWidthX, int nHeight,
                             int nHriFontType, int nHriFontPosition) {
        byte[] command = PrinterCommand.getBarCodeCommand(str, nType, nWidthX, nHeight, nHriFontType, nHriFontPosition);
        sendDataByte(command);
    }

    private boolean sendDataByte(byte[] data) {
        if (data==null || mService.getState() != BluetoothService.STATE_CONNECTED) {
            return false;
        }
        mService.write(data);
        return true;
    }

    // 根据Unicode编码完美的判断中文汉字和符号
    private static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION) {
            return true;
        }
        return false;
    }

    @Override
    public void onBluetoothServiceStateChanged(int state, Map<String, Object> boundle) {

    }


    public static byte[] printStoredImage(int var0) {
        if (var0 >= 1 && var0 <= 60) {
            return new byte[]{27, 102, (byte) (var0 - 1), 12};
        } else {
            Log.e("WoosimImage", "Invalid stored image number: " + var0);
            return null;
        }
    }

    public static byte[] printBitmap(int var0, int var1, int var2, int var3, Bitmap var4) {
        return printImage(var0, var1, var2, var3, var4, false, 0, false);
    }

    public static byte[] printCompressedBitmap(int var0, int var1, int var2, int var3, Bitmap var4) {
        return printImage(var0, var1, var2, var3, var4, true, 0, false);
    }

    public static byte[] printColorBitmap(int var0, int var1, int var2, int var3, Bitmap var4) {
        return printImage(var0, var1, var2, var3, var4, true, 2, false);
    }

    public static byte[] printColorBitmap(int var0, int var1, int var2, int var3, Bitmap var4, int var5) {
        return printImage(var0, var1, var2, var3, var4, true, var5, false);
    }

    public static byte[] printBitmapLandscape(int var0, int var1, int var2, int var3, Bitmap var4) {
        return printImage(var0, var1, var2, var3, var4, false, 0, true);
    }

    private static byte[] printImage(int var0, int var1, int var2, int var3, Bitmap var4, boolean var5, int var6,
                                     boolean var7) {
        if (var2 <= 0) {
            var2 = var4.getWidth();
        }

        if (var3 <= 0) {
            var3 = var4.getHeight();
        }

        ByteArrayOutputStream var8 = new ByteArrayOutputStream(1024);
        byte var9 = (byte) (var0 & 255);
        byte var10 = (byte) (var0 >> 8 & 255);
        byte var11 = (byte) (var1 & 255);
        byte var12 = (byte) (var1 >> 8 & 255);
        byte var13 = (byte) (var2 & 255);
        byte var14 = (byte) (var2 >> 8 & 255);
        byte var15 = (byte) (var3 & 255);
        byte var16 = (byte) (var3 >> 8 & 255);
        byte[] var17 = new byte[]{27, 87, var9, var10, var11, var12, var13, var14, var15, var16};
        var8.write(var17, 0, var17.length);
        byte[] var18;
        if (var7) {
            var18 = new byte[]{27, 84, 3};
            var8.write(var18, 0, var18.length);
        }

        var18 = convertBMPtoX4image(var4, var6);
        int var20 = var4.getWidth();
        int var21 = var4.getHeight();
        int var22 = var20 / 8 + (var20 % 8 == 0 ? 0 : 1);

        int var19;
        byte[] var23;
        byte[] var24;
        byte[] var25;
        for (var19 = 0; var19 < var21 / 255; ++var19) {
            if (var5) {
                var23 = new byte[]{27, 88, 51, (byte) var22, -1};
                var24 = convertImageX4toX3(var18, var22 * 255 * var19, var22 * 255);
                var25 = new byte[]{27, 88, 50, -1};
                var8.write(var23, 0, var23.length);
                var8.write(var24, 0, var24.length);
                var8.write(var25, 0, var25.length);
            } else {
                var23 = new byte[]{27, 88, 52, (byte) var22, -1};
                var8.write(var23, 0, var23.length);
                var8.write(var18, var22 * 255 * var19, var22 * 255);
            }

            int var27 = 255 * (var19 + 1);
            byte var28 = (byte) (var27 & 255);
            byte var29 = (byte) (var27 >> 8 & 255);
            byte[] var26 = new byte[]{27, 79, 0, 0, var28, var29};
            if (var7) {
                var26[2] = var28;
                var26[3] = var29;
                var26[4] = 0;
                var26[5] = 0;
            }

            var8.write(var26, 0, var26.length);
        }

        if (var21 % 255 != 0) {
            if (var5) {
                var23 = new byte[]{27, 88, 51, (byte) var22, (byte) (var21 % 255)};
                var24 = convertImageX4toX3(var18, var22 * 255 * var19, var22 * (var21 % 255));
                var25 = new byte[]{27, 88, 50, (byte) (var21 % 255)};
                var8.write(var23, 0, var23.length);
                var8.write(var24, 0, var24.length);
                var8.write(var25, 0, var25.length);
            } else {
                var23 = new byte[]{27, 88, 52, (byte) var22, (byte) (var21 % 255)};
                var8.write(var23, 0, var23.length);
                var8.write(var18, var22 * 255 * var19, var22 * (var21 % 255));
            }
        }

        var8.write(cmd_ESCFF, 0, cmd_ESCFF.length);
        return var8.toByteArray();
    }

    private static byte[] convertBMPtoX4image(Bitmap var0, int var1) {
        Bitmap var2 = var1 > 1 ? convertGrayscale(var0) : var0;
        int var3 = var2.getWidth();
        int var4 = var2.getHeight();
        int[] var5 = new int[var4 * var3];
        var2.getPixels(var5, 0, var3, 0, 0, var3, var4);
        int var6 = var3 / 8 + (var3 % 8 == 0 ? 0 : 1);
        byte[] var7 = new byte[var6 * var4];
        Arrays.fill(var7, (byte) 0);
        Random var8 = new Random();

        for (int var9 = 0; var9 < var4; ++var9) {
            for (int var10 = 0; var10 < var3; ++var10) {
                int var11 = var10 / 8;
                int var12 = var10 % 8;
                int var13 = var9 * var3 + var10;
                int var14 = var5[var13];
                int var15 = var9 * var6 + var11;
                switch (var1) {
                    case 0:
                        int var16 = Color.red(var14) + Color.green(var14) + Color.blue(var14);
                        if (var16 < 702 && var14 != 0) {
                            var7[var15] = (byte) (var7[var15] | 1 << 7 - var12);
                        }
                        break;
                    case 1:
                        double var17 = (double) (((float) Color.red(var14) * 0.21F + (float) Color.green(var14) * 0.71F + (float) Color.blue(var14) * 0.07F) / 255.0F);
                        if (var17 <= threshold[var8.nextInt(threshold.length)] && var14 != 0) {
                            var7[var15] = (byte) (var7[var15] | 1 << 7 - var12);
                        }
                        break;
                    case 2:
                        int var19 = Color.blue(var14) < 128 ? 0 : 255;
                        int var20 = Color.blue(var14) - var19;
                        if (var19 == 0 && var14 != 0) {
                            var7[var15] = (byte) (var7[var15] | 1 << 7 - var12);
                        }

                        if (var10 + 1 < var3 && var5[var13 + 1] != 0) {
                            var5[var13 + 1] += var20 * 7 >> 4;
                        }

                        if (var9 + 1 != var4) {
                            if (var10 > 0 && var5[var13 + var3 - 1] != 0) {
                                var5[var13 + var3 - 1] += var20 * 3 >> 4;
                            }

                            if (var5[var13 + var3] != 0) {
                                var5[var13 + var3] += var20 * 5 >> 4;
                            }

                            if (var10 + 1 < var3 && var5[var13 + var3 + 1] != 0) {
                                var5[var13 + var3 + 1] += var20 >> 4;
                            }
                        }
                }
            }
        }

        return var7;
    }

    private static Bitmap convertGrayscale(Bitmap var0) {
        int var1 = var0.getHeight();
        int var2 = var0.getWidth();
        Rect var3 = new Rect(0, 0, var2, var1);
        Bitmap var4 = Bitmap.createBitmap(var2, var1, Config.ARGB_8888);
        Canvas var5 = new Canvas(var4);
        Paint var6 = new Paint();
        ColorMatrix var7 = new ColorMatrix();
        var7.setSaturation(0.0F);
        ColorMatrixColorFilter var8 = new ColorMatrixColorFilter(var7);
        var6.setColorFilter(var8);
        var5.drawBitmap(var0, (Rect) null, var3, var6);
        return var4;
    }

    private static byte[] convertImageX4toX3(byte[] var0, int var1, int var2) {
        ByteArrayOutputStream var3 = new ByteArrayOutputStream(1024);
        byte var4 = var0[var1];
        int var5 = 0;
        int var6 = 1;

        for (int var7 = 0; var7 < var2; ++var7) {
            byte var8 = var0[var1 + var7];
            if (var4 == var8) {
                ++var5;
                if (var5 >= 3 && var6 > 1) {
                    var3.write(128 + var6 - 1);
                    var3.write(var0, var1 + var7 - var6 - 1, var6 - 1);
                    var6 = 1;
                }

                if (var5 > 62) {
                    var3.write(254);
                    var3.write(var4);
                    var5 = 1;
                }
            } else {
                ++var6;
                if (var5 >= 3) {
                    var3.write(192 + var5);
                    var3.write(var4);
                    --var6;
                } else if (var5 == 2) {
                    ++var6;
                }

                if (var6 > 62) {
                    var3.write(190);
                    var3.write(var0, var1 + var7 - var6 + 1, 62);
                    var6 -= 62;
                }

                var5 = 1;
            }

            var4 = var8;
        }

        if (var5 >= 3) {
            var3.write(192 + var5);
            var3.write(var4);
        } else {
            if (var5 == 2) {
                ++var6;
            }

            var3.write(128 + var6);
            var3.write(var0, var1 + var2 - var6, var6);
        }

        return var3.toByteArray();
    }

    public static byte[] fastPrintBitmap(int var0, int var1, int var2, int var3, Bitmap var4) {
        if (var2 <= 0) {
            var2 = var4.getWidth();
        }

        if (var3 <= 0) {
            var3 = var4.getHeight();
        }

        ByteArrayOutputStream var5 = new ByteArrayOutputStream(1024);
        byte var6 = (byte) (var0 & 255);
        byte var7 = (byte) (var0 >> 8 & 255);
        byte var8 = (byte) (var1 & 255);
        byte var9 = (byte) (var1 >> 8 & 255);
        byte var10 = (byte) (var2 & 255);
        byte var11 = (byte) (var2 >> 8 & 255);
        byte[] var12 = convertBMPtoX4image(var4, 0);
        int var14 = var4.getWidth();
        int var15 = var4.getHeight();
        int var16 = var14 / 8 + (var14 % 8 == 0 ? 0 : 1);
        boolean var17 = true;
        if (var15 > var3) {
            var15 = var3;
        }

        var5.write(24);

        int var13;
        byte[] var18;
        byte[] var19;
        for (var13 = 0; var13 < var15 / 255; ++var13) {
            var18 = new byte[]{27, 87, var6, var7, 0, 0, var10, var11, -1, 0};
            if (var17) {
                var18[4] = var8;
                var18[5] = var9;
                var17 = false;
            }

            var5.write(var18, 0, var18.length);
            var19 = new byte[]{27, 88, 52, (byte) var16, -1};
            var5.write(var19, 0, var19.length);
            var5.write(var12, var16 * 255 * var13, var16 * 255);
            var5.write(cmd_ESCFF, 0, cmd_ESCFF.length);
            var5.write(24);
        }

        if (var15 % 255 != 0) {
            var18 = new byte[]{27, 87, var6, var7, 0, 0, var10, var11, (byte) (var15 % 255), 0};
            if (var17) {
                var18[4] = var8;
                var18[5] = var9;
            }

            var5.write(var18, 0, var18.length);
            var19 = new byte[]{27, 88, 52, (byte) var16, (byte) (var15 % 255)};
            var5.write(var19, 0, var19.length);
            var5.write(var12, var16 * 255 * var13, var16 * (var15 % 255));
            var5.write(cmd_ESCFF, 0, cmd_ESCFF.length);
            var5.write(24);
        }

        return var5.toByteArray();
    }

    public static byte[] drawBitmap(int var0, int var1, Bitmap var2) {
        return drawImage(var0, var1, var2, 0);
    }

    public static byte[] drawColorBitmap(int var0, int var1, Bitmap var2) {
        return drawImage(var0, var1, var2, 2);
    }

    private static byte[] drawImage(int var0, int var1, Bitmap var2, int var3) {
        int var4 = var2.getWidth();
        int var5 = var2.getHeight();
        int var6 = var4 / 8 + (var4 % 8 == 0 ? 0 : 1);
        byte var7 = (byte) (var0 & 255);
        byte var8 = (byte) (var0 >> 8 & 255);
        byte var9 = (byte) (var1 & 255);
        byte var10 = (byte) (var1 >> 8 & 255);
        byte var11 = (byte) (var4 & 255);
        byte var12 = (byte) (var4 >> 8 & 255);
        byte var13 = (byte) (var5 & 255);
        byte var14 = (byte) (var5 >> 8 & 255);
        byte[] var15 = new byte[]{27, 87, var7, var8, var9, var10, var11, var12, var13, var14};
        byte[] var16 = new byte[]{27, 88, 52, (byte) var6, (byte) var5};
        byte[] var17 = convertBMPtoX4image(var2, var3);
        ByteBuffer var18 = ByteBuffer.allocate(var15.length + var16.length + var17.length);
        var18.put(var15);
        var18.put(var16);
        var18.put(var17);
        return var18.array();
    }

    public static byte[] drawBox(int var0, int var1, int var2, int var3, int var4) {
        if (var2 <= 0 && var3 <= 0) {
            Log.e("WoosimImage", "Invalid parameters on width and/or height.");
            return null;
        } else {
            byte var5 = (byte) (var0 & 255);
            byte var6 = (byte) (var0 >> 8 & 255);
            byte var7 = (byte) (var1 & 255);
            byte var8 = (byte) (var1 >> 8 & 255);
            byte var9 = (byte) (var2 & 255);
            byte var10 = (byte) (var2 >> 8 & 255);
            byte var11 = (byte) (var3 & 255);
            byte var12 = (byte) (var3 >> 8 & 255);
            return new byte[]{27, 79, var5, var6, var7, var8, 29, 105, var9, var10, var11, var12, (byte) var4};
        }
    }

    public static byte[] drawLine(int var0, int var1, int var2, int var3, int var4) {
        if (var0 >= 0 && var1 >= 0 && var2 >= 0 && var3 >= 0 && var4 > 0) {
            if (var4 > 255) {
                var4 = 255;
            }

            byte var5 = (byte) (var0 & 255);
            byte var6 = (byte) (var0 >> 8 & 255);
            byte var7 = (byte) (var1 & 255);
            byte var8 = (byte) (var1 >> 8 & 255);
            byte var9 = (byte) (var2 & 255);
            byte var10 = (byte) (var2 >> 8 & 255);
            byte var11 = (byte) (var3 & 255);
            byte var12 = (byte) (var3 >> 8 & 255);
            byte var13 = (byte) (var4 & 255);
            return new byte[]{27, 103, 49, var5, var6, var7, var8, var9, var10, var11, var12, var13};
        } else {
            Log.e("WoosimImage", "Invalid parameter.");
            return null;
        }
    }

    public static byte[] drawEllipse(int var0, int var1, int var2, int var3, int var4) {
        if (var0 >= 0 && var1 >= 0 && var2 > 0 && var3 > 0 && var4 > 0) {
            if (var4 > 255) {
                var4 = 255;
            }

            byte var5 = (byte) (var0 & 255);
            byte var6 = (byte) (var0 >> 8 & 255);
            byte var7 = (byte) (var1 & 255);
            byte var8 = (byte) (var1 >> 8 & 255);
            byte var9 = (byte) (var2 & 255);
            byte var10 = (byte) (var2 >> 8 & 255);
            byte var11 = (byte) (var3 & 255);
            byte var12 = (byte) (var3 >> 8 & 255);
            byte var13 = (byte) (var4 & 255);
            return new byte[]{27, 103, 50, var5, var6, var7, var8, var9, var10, var11, var12, var13};
        } else {
            Log.e("WoosimImage", "Invalid parameter.");
            return null;
        }
    }

    public static byte[] printARGBbitmap(int var0, int var1, int var2, int var3, Bitmap var4) {
        Bitmap var5 = removeAlphaValue(var4);
        byte[] var6 = printRGBbitmap(var0, var1, var2, var3, var5);
        var5.recycle();
        return var6;
    }

    public static byte[] printRGBbitmap(int var0, int var1, int var2, int var3, Bitmap var4) {
        return printBitmap(var0, var1, var2, var3, var4);
    }

    public static byte[] bmp2PrintableImage(int var0, int var1, int var2, int var3, Bitmap var4) {
        return printRGBbitmap(var0, var1, var2, var3, var4);
    }

    private static Bitmap removeAlphaValue(Bitmap var0) {
        Bitmap var1 = var0.copy(var0.getConfig(), true);
        int var2 = var1.getWidth();
        int var3 = var1.getHeight();

        for (int var4 = 0; var4 < var2; ++var4) {
            for (int var5 = 0; var5 < var3; ++var5) {
                if (var1.getPixel(var4, var5) == 0) {
                    var1.setPixel(var4, var5, -1);
                }
            }
        }

        return var1;
    }

    public static byte[] putARGBbitmap(int var0, int var1, Bitmap var2) {
        Bitmap var3 = removeAlphaValue(var2);
        byte[] var4 = putRGBbitmap(var0, var1, var3);
        var3.recycle();
        return var4;
    }

    public static byte[] putRGBbitmap(int var0, int var1, Bitmap var2) {
        return drawBitmap(var0, var1, var2);
    }

    public static byte[] fastPrintARGBbitmap(int var0, int var1, int var2, int var3, Bitmap var4) {
        Bitmap var5 = removeAlphaValue(var4);
        byte[] var6 = fastPrintRGBbitmap(var0, var1, var2, var3, var5);
        var5.recycle();
        return var6;
    }

    public static byte[] fastPrintRGBbitmap(int var0, int var1, int var2, int var3, Bitmap var4) {
        return fastPrintBitmap(var0, var1, var2, var3, var4);
    }

    /****************************************************************************************************/

    private static class ColumnSplitedString{
        private int shorter;
        private String str;

        public ColumnSplitedString(int shorter, String str) {
            this.shorter = shorter;
            this.str = str;
        }

        public int getShorter() {
            return shorter;
        }

        public String getStr() {
            return str;
        }
    }

}
