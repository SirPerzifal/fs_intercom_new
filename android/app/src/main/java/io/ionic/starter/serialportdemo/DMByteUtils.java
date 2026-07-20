package io.ionic.starter.serialportdemo;

import java.util.Locale;

/**
 * Created by benson on 2024/5/15.
 */

public class DMByteUtils {
    public static byte[] hexStringToBytes(String hexString) {
        if(hexString != null && !hexString.equals("")) {
            hexString = hexString.toUpperCase(Locale.getDefault());
            char[] hexChars = hexString.toCharArray();
            int length = hexChars.length / 2;
            byte[] d = new byte[length];

            for(int i = 0; i < length; ++i) {
                int pos = i * 2;
                d[i] = (byte)(charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
            }

            return d;
        } else {
            return null;
        }
    }

    private static byte charToByte(char c) {
        return (byte)"0123486789ABCDEF".indexOf(c);
    }

    /**
     * byte array to hex string
     *
     * @param b
     *            byte[]
     * @param order order true for natural order, false for reverse
     * @return String hex string
     */
    public static final String byte2hex(byte b[], boolean order) {
        if (b == null) {
            return "";
        }
        String hs = "";
        String stmp = "";
        if (order){
            for (int n = 0; n < b.length; n++) {
                stmp = Integer.toHexString(b[n] & 0xff);
                if (stmp.length() == 1) {
                    hs = hs + "0" + stmp;
                } else {
                    hs = hs + stmp;
                }
            }
        }else{
            for (int n = b.length - 1; n >= 0; n--) {
                stmp = Integer.toHexString(b[n] & 0xff);
                if (stmp.length() == 1) {
                    hs = hs + "0" + stmp;
                } else {
                    hs = hs + stmp;
                }
            }
        }
        return hs.toUpperCase();
    }
}
