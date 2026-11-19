package org.bartalap.tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bartalap: finds payment info (bKash / Nagad / Rocket / Upay) in a pasted SMS or chat message.
 * Pure Java so it can be unit tested anywhere.
 */
public final class TrxDetector {

    private TrxDetector() {}

    public static final class Result {
        public String method = "";
        public String trxId = "";
        public String amount = "";
        public String number = "";

        public boolean isEmpty() {
            return trxId.isEmpty() && amount.isEmpty() && number.isEmpty();
        }

        public String toBangla() {
            StringBuilder sb = new StringBuilder();
            sb.append("মাধ্যম: ").append(method.isEmpty() ? "অজানা" : method).append('\n');
            sb.append("TrxID: ").append(trxId.isEmpty() ? "পাওয়া যায়নি" : trxId).append('\n');
            sb.append("টাকা: ").append(amount.isEmpty() ? "পাওয়া যায়নি" : amount).append('\n');
            sb.append("নাম্বার: ").append(number.isEmpty() ? "পাওয়া যায়নি" : number);
            return sb.toString();
        }
    }

    private static final Pattern TRX = Pattern.compile(
            "(?i)(?:trx\\s*id|txn\\s*id|trxid|txnid|transaction\\s*id|ট্রানজেকশন\\s*আইডি)\\s*[:#.\\-]?\\s*([A-Z0-9]{8,12})");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(?:tk|bdt|৳|টাকা)\\s*[:.]?\\s*([0-9০-৯][0-9০-৯,]*(?:\\.[0-9০-৯]{1,2})?)");
    private static final Pattern AMOUNT_AFTER = Pattern.compile(
            "(?i)([0-9০-৯][0-9০-৯,]*(?:\\.[0-9০-৯]{1,2})?)\\s*(?:tk|bdt|৳|টাকা)");
    private static final Pattern NUMBER = Pattern.compile("(?<![0-9])(01[3-9][0-9]{8})(?![0-9])");

    public static Result detect(String text) {
        Result r = new Result();
        if (text == null) return r;
        String t = toEnglishDigits(text);
        String lower = t.toLowerCase();

        if (lower.contains("bkash") || t.contains("বিকাশ")) r.method = "bKash";
        else if (lower.contains("nagad") || t.contains("নগদ")) r.method = "Nagad";
        else if (lower.contains("rocket") || lower.contains("dbbl") || t.contains("রকেট")) r.method = "Rocket";
        else if (lower.contains("upay") || t.contains("উপায়")) r.method = "Upay";

        Matcher m = TRX.matcher(t);
        if (m.find()) r.trxId = m.group(1).toUpperCase();

        m = AMOUNT.matcher(t);
        if (m.find()) {
            r.amount = m.group(1).replace(",", "");
        } else {
            m = AMOUNT_AFTER.matcher(t);
            if (m.find()) r.amount = m.group(1).replace(",", "");
        }

        m = NUMBER.matcher(t);
        if (m.find()) r.number = m.group(1);
        return r;
    }

    public static String toEnglishDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '০' && c <= '৯') sb.append((char) ('0' + (c - '০')));
            else sb.append(c);
        }
        return sb.toString();
    }
}
