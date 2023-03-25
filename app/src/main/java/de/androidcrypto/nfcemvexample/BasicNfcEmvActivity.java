package de.androidcrypto.nfcemvexample;

import static de.androidcrypto.nfcemvexample.BinaryUtils.bytesToHex;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.devnied.emvnfccard.utils.TlvUtil;
import com.payneteasy.tlv.BerTag;
import com.payneteasy.tlv.BerTlv;
import com.payneteasy.tlv.BerTlvParser;
import com.payneteasy.tlv.BerTlvs;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.androidcrypto.nfcemvexample.nfccreditcards.DolValues;
import de.androidcrypto.nfcemvexample.sasc.DOL;
import de.androidcrypto.nfcemvexample.sasc.TagAndLength;

public class BasicNfcEmvActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    private final String TAG = "BasicNfcEmvAct";
    private com.google.android.material.textfield.TextInputEditText etLog;
    private View loadingLayout;
    private NfcAdapter mNfcAdapter;

    private String outputString = ""; // used for the UI output
    private String exportString = ""; // used for exporting the log to a text file
    private String exportStringFileName = "emv.html";
    private final String stepSeparatorString = "*********************************";
    private final String lineSeparatorString = "---------------------------------";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic_nfc_emv);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.basic_toolbar);
        setSupportActionBar(myToolbar);

        etLog = findViewById(R.id.etLog);
        loadingLayout = findViewById(R.id.loading_layout);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    /**
     * section for NFC
     */

    /**
     * This method is run in another thread when a card is discovered
     * This method cannot cannot direct interact with the UI Thread
     * Use `runOnUiThread` method to change the UI from this method
     *
     * @param tag discovered tag
     */

    @Override
    public void onTagDiscovered(Tag tag) {
        clearData(etLog);
        Log.d(TAG, "NFC tag discovered");
        writeToUiAppend("NFC tag discovered");
        setLoadingLayoutVisibility(true);
        byte[] tagId = tag.getId();
        writeToUiAppend("TagId: " + bytesToHexNpe(tagId));
        String[] techList = tag.getTechList();
        writeToUiAppend("TechList found with these entries:");
        boolean isoDepInTechList = false;
        for (int i = 0; i < techList.length; i++) {
            writeToUiAppend(techList[i]);
            if (techList[i].equals("android.nfc.tech.IsoDep")) isoDepInTechList = true;
        }
        // proceed only if tag has IsoDep in the techList
        if (isoDepInTechList) {
            IsoDep nfc = null;
            nfc = IsoDep.get(tag);
            if (nfc != null) {
                try {
                    nfc.connect();
                    Log.d(TAG, "connection with card success");
                    writeToUiAppend("connection with card success");
                    // here we are going to start our journey through the card

                    printStepHeader(0, "our journey begins");

                    writeToUiAppend("abc");

                    /**
                     * step 1 code start
                     */

                    printStepHeader(1, "select PPSE");
                    byte[] PPSE = "2PAY.SYS.DDF01".getBytes(StandardCharsets.UTF_8); // PPSE
                    byte[] selectPpseCommand = selectApdu(PPSE);
                    byte[] selectPpseResponse = nfc.transceive(selectPpseCommand);
                    writeToUiAppend("01 select PPSE command  length " + selectPpseCommand.length + " data: " + bytesToHexNpe(selectPpseCommand));
                    writeToUiAppend("01 select PPSE response length " + selectPpseResponse.length + " data: " + bytesToHexNpe(selectPpseResponse));
                    writeToUiAppend(prettyPrintDataToString(selectPpseResponse));

                    byte[] selectPpseResponseOk = checkResponse(selectPpseResponse);
                    // proceed only when te do have a positive read result = 0x'9000' at the end of response data
                    if (selectPpseResponseOk != null) {

                        /**
                         * step 1 code end
                         */

                        // get the tags from respond
                        BerTlvParser parserA = new BerTlvParser();
                        BerTlvs tlvs = parserA.parse(selectPpseResponseOk, 0, selectPpseResponseOk.length);
                        List<BerTlv> selectPpseResponseTagList = tlvs.getList();
                        int selectPpseResponseTagListSize = selectPpseResponseTagList.size();
                        writeToUiAppend("found " + selectPpseResponseTagListSize + " tags in response");
                        // show iterating
                        for (int i = 0; i < selectPpseResponseTagListSize; i++) {
                            BerTlv tlv = selectPpseResponseTagList.get(i);
                            writeToUiAppend(tlv.toString());
                            BerTag berTag = tlv.getTag();
                            boolean berTagIsConstructed = berTag.isConstructed();

                        }

                        /*
                        // devnied
                        writeToUiAppend("");
                        List<TagAndLength> parsedList = TlvUtil.parseTagAndLength(selectPpseResponseOk);
                        int parsedListSize = parsedList.size();
                        writeToUiAppend("parsedListSize: " + parsedListSize);
                        writeToUiAppend(parsedList.toString());
*/
                        /*
                        // this is working
                        writeToUiAppend("");
                        writeToUiAppend("using TagListParser");
                        List<TagNameValue> parsedList = TagListParser.parseRespond(selectPpseResponseOk);
                        int parsedListSize = parsedList.size();
                        writeToUiAppend("parsedListSize: " + parsedListSize);
                        for (int i = 0; i < parsedListSize; i++) {
                            TagNameValue p = parsedList.get(i);
                            writeToUiAppend("tag " + i + " : " + bytesToHexNpe(p.getTagBytes()) + " has the value " + bytesToHexNpe(p.getTagValueBytes()));
                            writeToUiAppend("tag " + i + " : " + bytesToHexNpe(p.getTagBytes()) + " has the name " + p.getTagName());
                        }

                         */

                        /**
                         * step 2 code start
                        */

                        writeToUiAppend("");
                        printStepHeader(2, "search applications on card");
                        writeToUiAppend("02 analyze select PPSE response and search for tag 0x4F (applications on card)");

                        BerTlvParser parser = new BerTlvParser();
                        BerTlvs tlv4Fs = parser.parse(selectPpseResponseOk);
                        // by searching for tag 4f
                        List<BerTlv> tag4fList = tlv4Fs.findAll(new BerTag(0x4F));
                        if (tag4fList.size() < 1) {
                            writeToUiAppend("there is no tag 0x4F available, stopping here");
                            setLoadingLayoutVisibility(false);
                            try {
                                nfc.close();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return;
                        }
                        writeToUiAppend("Found tag 0x4F " + tag4fList.size() + (tag4fList.size()==1?" time:":" times:"));
                        ArrayList<byte[]> aidList = new ArrayList<>();
                        for (int i4f = 0; i4f < tag4fList.size(); i4f++) {
                            BerTlv tlv4f = tag4fList.get(i4f);
                            byte[] tlv4fBytes = tlv4f.getBytesValue();
                            aidList.add(tlv4fBytes);
                            writeToUiAppend("application Id (AID): " + bytesToHexNpe(tlv4fBytes));
                        }

                        /**
                         * step 2 code end
                         */

                        /**
                         * step 3 code start
                         */

                        // step 03: iterating through aidList by selecting AID
                        for (int aidNumber = 0; aidNumber < tag4fList.size(); aidNumber++) {
                            byte[] aidSelected = aidList.get(aidNumber);
                            writeToUiAppend("");
                            printStepHeader(3, "select application by AID");
                            writeToUiAppend("03 select application by AID " + bytesToHexNpe(aidSelected) + " (number " + (aidNumber + 1) + ")");
                            byte[] selectAidCommand = selectApdu(aidSelected);
                            byte[] selectAidResponse = nfc.transceive(selectAidCommand);
                            writeToUiAppend("");
                            writeToUiAppend("03 select AID command  length " + selectAidCommand.length + " data: " + bytesToHexNpe(selectAidCommand));
                            writeToUiAppend("03 select AID response length " + selectAidResponse.length + " data: " + bytesToHexNpe(selectAidResponse));
                            writeToUiAppend(prettyPrintDataToString(selectAidResponse));

                        //}
                            /**
                             * step 3 code end
                             */

                            // intermediate step - get single data from card, will be printed later
                            writeToUiAppend("");
                            writeToUiAppend("get single data elements");
                            byte[] applicationTransactionCounter = getApplicationTransactionCounter(nfc);
                            byte[] pinTryCounter = getPinTryCounter(nfc);
                            byte[] lastOnlineATCRegister = getLastOnlineATCRegister(nfc);
                            byte[] logFormat = getLogFormat(nfc);
                            // print single data
                            writeToUiAppend(dumpSingleData(applicationTransactionCounter, pinTryCounter, lastOnlineATCRegister, logFormat));
                            writeToUiAppend("");


                            /**
                             * step 4 code start
                             */

                            byte[] selectAidResponseOk = checkResponse(selectAidResponse);
                            if (selectAidResponseOk != null) {
                                writeToUiAppend("");
                                printStepHeader(4, "search for tag 0x9F38");
                                writeToUiAppend("04 search for tag 0x9F38 in the selectAid response");
                                /**
                                 * note: different behaviour between VisaCard, Mastercard and German GiroCards
                                 * Mastercard has NO PDOL, Visa gives PDOL in tag 9F38
                                 * next step: search for tag 9F38 Processing Options Data Object List (PDOL)
                                 */
                                BerTlvs tlvsAid = parser.parse(selectAidResponseOk);
                                BerTlv tag9f38 = tlvsAid.find(new BerTag(0x9F, 0x38));
                                byte[] gpoRequestCommand;
                                if (tag9f38 != null) {
                                    /**
                                     * the following code is for VisaCards and (German) GiroCards as we found a PDOL
                                     */
                                    writeToUiAppend("");
                                    writeToUiAppend("### processing the VisaCard and GiroCard path ###");
                                    writeToUiAppend("");
                                    byte[] pdolValue = tag9f38.getBytesValue();
                                    writeToUiAppend("found tag 0x9F38 in the selectAid with this length: " + pdolValue.length + " data: " + bytesToHexNpe(pdolValue));

                                    // using code from DOL.java
                                    DOL pdol = new DOL(DOL.Type.PDOL, pdolValue);
                                    writeToUiAppend("");
                                    writeToUiAppend(pdol.toString());

                                    // using TagAndLength
                                    List<TagAndLength> pdolList = pdol.getTagAndLengthList();
                                    int pdolListSize = pdolList.size();
                                    writeToUiAppend("The card is requesting " + pdolListSize + (pdolListSize==1?" tag":" tags") + " with length:");
                                    for (int i = 0; i < pdolListSize; i++) {
                                        TagAndLength pdolEntry = pdolList.get(i);
                                        //writeToUiAppend("tag " + (i + 1) + " : " + pdolEntry.getTag().getName() + " [" +
                                        writeToUiAppend("tag " + String.format("%02d", i + 1) + ": " + pdolEntry.getTag().getName() + " [" +
                                                bytesToHexNpe(pdolEntry.getTag().getTagBytes()) +
                                                "] length " + String.valueOf(pdolEntry.getLength()));
                                    }

/*
VisaCard:
I/System.out: The card is requesting 9 tags with length:
I/System.out: tag 01: Terminal Transaction Qualifiers [9f66] length 4
I/System.out: tag 02: Amount, Authorised (Numeric) [9f02] length 6
I/System.out: tag 03: Amount, Other (Numeric) [9f03] length 6
I/System.out: tag 04: Terminal Country Code [9f1a] length 2
I/System.out: tag 05: Terminal Verification Results (TVR) [95] length 5
I/System.out: tag 06: Transaction Currency Code [5f2a] length 2
I/System.out: tag 07: Transaction Date [9a] length 3
I/System.out: tag 08: Transaction Type [9c] length 1
I/System.out: tag 09: Unpredictable Number [9f37] length 4
 */
/*
VisaCard information about tags:
Terminal Transaction Qualifiers [9f66] C-3 Kernel 3 v2.10 page 108 + 109
Amount, Authorised (Numeric) [9f02]    C-3 Kernel 3 v2.10 page 81
Amount, Other (Numeric) [9f03]         C-3 Kernel 3 v2.10 page 81
Terminal Country Code [9f1a]           C-3 Kernel 3 v2.10 page 107



 */



                                    gpoRequestCommand = getGpoFromPdol(pdolValue);
                                } else { // if (tag9f38 != null) {
                                    /**
                                     * MasterCard code
                                     */
                                    writeToUiAppend("");
                                    writeToUiAppend("### processing the MasterCard path ###");
                                    writeToUiAppend("");

                                    writeToUiAppend("No PDOL found in the selectAid response, generating a 'null' PDOL");
                                    gpoRequestCommand = getGpoFromPdol(new byte[0]); // empty PDOL
                                }

                                writeToUiAppend("");
                                printStepHeader(5, "get the processing options");
                                writeToUiAppend("05 get the processing options  command length: " + gpoRequestCommand.length + " data: " + bytesToHex(gpoRequestCommand));

                                /**
                                 * step 5 code starts
                                 */

                                /**
                                 * WARNING: each get processing options request increases the icc internal
                                 * 'application transaction counter'. If the 2 byte long counter reaches the
                                 * maximum of '65535' (0xFFFF) the card will no longer accept any read commands
                                 * and the card is irretrievable damaged.
                                 * DO NOT RUN THIS COMMAND IN A LOOP !
                                 */

                                byte[] gpoRequestResponse = nfc.transceive(gpoRequestCommand);
                                if (gpoRequestResponse != null) {
                                    writeToUiAppend("05 get the processing options response length: " + gpoRequestResponse.length + " data: " + bytesToHex(gpoRequestResponse));
                                    byte[] gpoRequestResponseOk = checkResponse(gpoRequestResponse);
                                    if (gpoRequestResponseOk != null) {
                                        writeToUiAppend(prettyPrintDataToString(gpoRequestResponse));


                                    }
                                }

                                /**
                                 * step 5 code end
                                 */

                            } else { // if (selectAidResponseOk != null) {
                                writeToUiAppend("the selecting AID command failed");
                            }

                            /**
                             * step 4 code end
                             */



                        } // for (int aidNumber = 0; aidNumber < tag4fList.size(); aidNumber++) {

                        /**
                         * step 3 code end
                         */



                    } else {
                        // if (selectPpseResponseOk != null)
                        writeToUiAppend("the result of the reading was not successful so the workflow ends here, sorry.");
                        startEndSequence(nfc);
                    }

                    /*
                    https://www.europeanpaymentscouncil.eu/sites/default/files/KB/files/EPC050-16%20SCS%20Volume%207%201%20-%20Bul%2001%20-%2020160229%20-%20Book%202%20-%20EEA%20Product%20Identification%20and%20usage%20in%20Selection%20of%20Application.pdf
                    The first byte is defined as follows.
Value IFR Product Type
‘01’ Debit Product
‘02’ Credit Product
‘03’ Commercial Product
‘04’ Pre-paid Product
All other values Reserved for future use
Bytes 2 to 5 are reserved for future use by the CSG and if present, they shall be filled with '00' for the
current version.
Presence of Tag ‘9F0A’ with ID = ‘0001’ indicates an EEA issued card
                     Application Selection Registered Proprietary Data (Tag ‘9F0A’),

different returns
Visa comd debit:
MC AAB credit:

                     */

                    printStepHeader(99, "our journey ends");


                } catch (IOException e) {
                    writeToUiAppend("connection with card failure");
                    writeToUiAppend(e.getMessage());
                    playPing();
                    writeToUiFinal(etLog);
                    setLoadingLayoutVisibility(false);
                    // throw new RuntimeException(e);
                    return;
                }
            }
        } else {
            // if (isoDepInTechList) {
            writeToUiAppend("The discovered NFC tag does not have an IsoDep interface.");
        }
        // final cleanup
        playPing();
        writeToUiFinal(etLog);
        setLoadingLayoutVisibility(false);
    }

    private void startEndSequence(IsoDep nfc) {
        playPing();
        writeToUiFinal(etLog);
        setLoadingLayoutVisibility(false);
        try {
            nfc.close();
        } catch (IOException e) {
            // throw new RuntimeException(e);
        }
        return;
    }

    /**
     * section for emv reading
     */

    /**
     * step 5 code start
     */


    /**
     * step xx code start
     */

    /**
     * section for single read commands
     * overview: https://github.com/sasc999/javaemvreader/blob/master/src/main/java/sasc/emv/EMVAPDUCommands.java
     */

    // Get the data of ATC(Application Transaction Counter, tag '9F36')), template 77 or 80
    private byte[] getApplicationTransactionCounter(IsoDep nfc) {
        byte[] cmd = new byte[]{(byte) 0x80, (byte) 0xCA, (byte) 0x9F, (byte) 0x36, (byte) 0x00};
        byte[] result = new byte[0];
        try {
            result = nfc.transceive(cmd);
        } catch (IOException e) {
            System.out.println("* getApplicationTransactionCounter failed");
            return null;
        }
        System.out.println("*** getATC: " + bytesToHexNpe(result));
        // e.g. visa returns 9f360200459000
        // e.g. visa returns 9f36020045 9000
        byte[] resultOk = checkResponse(result);
        if (resultOk == null) {
            return null;
        } else {
            return getTagValueFromResult(resultOk, (byte) 0x9f, (byte) 0x36);
        }
    }

    private byte[] getPinTryCounter(IsoDep nfc) {
        byte[] cmd = new byte[]{(byte) 0x80, (byte) 0xCA, (byte) 0x9F, (byte) 0x17, (byte) 0x00};
        byte[] result = new byte[0];
        try {
            result = nfc.transceive(cmd);
        } catch (IOException e) {
            System.out.println("* getPinTryCounterCounter failed");
            return null;
        }
        byte[] resultOk = checkResponse(result);
        if (resultOk == null) {
            return null;
        } else {
            return getTagValueFromResult(resultOk, (byte) 0x9f, (byte) 0x17);
        }
    }

    private byte[] getLastOnlineATCRegister(IsoDep nfc) {
        byte[] cmd = new byte[]{(byte) 0x80, (byte) 0xCA, (byte) 0x9F, (byte) 0x13, (byte) 0x00};
        byte[] result = new byte[0];
        try {
            result = nfc.transceive(cmd);
        } catch (IOException e) {
            System.out.println("* getLastOnlineATCRegister failed");
            return null;
        }
        byte[] resultOk = checkResponse(result);
        if (resultOk == null) {
            return null;
        } else {
            return getTagValueFromResult(resultOk, (byte) 0x9f, (byte) 0x13);
        }
    }

    private byte[] getLogFormat(IsoDep nfc) {
        byte[] cmd = new byte[]{(byte) 0x80, (byte) 0xCA, (byte) 0x9F, (byte) 0x4F, (byte) 0x00};
        byte[] result = new byte[0];
        try {
            result = nfc.transceive(cmd);
        } catch (IOException e) {
            System.out.println("* getLastOnlineATCRegister failed");
            return null;
        }
        byte[] resultOk = checkResponse(result);
        if (resultOk == null) {
            return null;
        } else {
            return getTagValueFromResult(resultOk, (byte) 0x9f, (byte) 0x4F);
        }
    }

    /**
     * gets the byte value of a tag from transceive response
     *
     * @param data
     * @param search
     * @return
     */
    private byte[] getTagValueFromResult(byte[] data, byte... search) {
        int argumentsLength = search.length;
        if (argumentsLength < 1) return null;
        if (argumentsLength > 2) return null;
        if (data.length > 253) return null;
        BerTlvParser parser = new BerTlvParser();
        BerTlvs tlvDatas = parser.parse(data);
        BerTlv tag;
        if (argumentsLength == 1) {
            tag = tlvDatas.find(new BerTag(search[0]));
        } else {
            tag = tlvDatas.find(new BerTag(search[0], search[1]));
        }
        byte[] tagBytes;
        if (tag == null) {
            return null;
        } else {
            return tag.getBytesValue();
        }
    }

    /**
     * printout of 4 single data elements
     * this method is Null Pointer Exception (NPE) safe
     * @param applicationTransactionCounter
     * @param pinTryCounter
     * @param lastOnlineATCRegister
     * @param logFormat
     * @return
     */
    private String dumpSingleData(byte[] applicationTransactionCounter, byte[] pinTryCounter, byte[] lastOnlineATCRegister, byte[] logFormat) {
        StringBuilder sb = new StringBuilder();
        sb.append("single data retrieved from card").append("\n");
        if (applicationTransactionCounter != null) {
            sb.append("applicationTransactionCounter: ").append(bytesToHex(applicationTransactionCounter)).append(" (hex), ").append(BinaryUtils.intFromByteArrayV4(applicationTransactionCounter)).append(" (dec)").append("\n");
        } else {
            sb.append("applicationTransactionCounter: NULL").append("\n");
        }
        if (pinTryCounter != null) {
            sb.append("pinTryCounter: ").append(bytesToHex(pinTryCounter)).append("\n");
        } else {
            sb.append("pinTryCounter: NULL").append("\n");
        }
        if (lastOnlineATCRegister != null) {
            sb.append("lastOnlineATCRegister: ").append(bytesToHex(lastOnlineATCRegister)).append("\n");
        } else {
            sb.append("lastOnlineATCRegister: NULL").append("\n");
        }
        if (logFormat != null) {
            sb.append("logFormat: ").append(bytesToHex(logFormat)).append("\n");
        } else {
            sb.append("logFormat: NULL").append("\n");
        }
        return sb.toString();
    }

    /**
     * step xx code end
     */


    /**
     * DOL utilities
     */

    private byte[] getGpoFromPdol(@NonNull byte[] pdol) {
        // get the tags in a list
        List<com.github.devnied.emvnfccard.iso7816emv.TagAndLength> tagAndLength = TlvUtil.parseTagAndLength(pdol);
        int tagAndLengthSize = tagAndLength.size();
        if (tagAndLengthSize < 1) {
            // there are no pdols in the list
            //Log.e(TAG, "there are no PDOLs in the pdol array, aborted");
            //return null;
            // returning an empty PDOL
            String tagLength2d = "00"; // length value
            String tagLength2dAnd2 = "02"; // length value + 2
            String constructedGpoCommandString = "80A80000" + tagLength2dAnd2 + "83" + tagLength2d + "" + "00";
            return hexToBytes(constructedGpoCommandString);
        }
        int valueOfTagSum = 0; // total length
        StringBuilder sb = new StringBuilder(); // takes the default values of the tags
        DolValues dolValues = new DolValues();
        for (int i = 0; i < tagAndLengthSize; i++) {
            // get a single tag
            com.github.devnied.emvnfccard.iso7816emv.TagAndLength tal = tagAndLength.get(i); // eg 9f3704
            byte[] tagToSearch = tal.getTag().getTagBytes(); // gives the tag 9f37
            int lengthOfTag = tal.getLength(); // 4
            valueOfTagSum += tal.getLength(); // add it to the sum
            // now we are trying to find a default value
            byte[] defaultValue = dolValues.getDolValue(tagToSearch);
            byte[] usedValue = new byte[0];
            if (defaultValue != null) {
                if (defaultValue.length > lengthOfTag) {
                    // cut it to correct length
                    usedValue = Arrays.copyOfRange(defaultValue, 0, lengthOfTag);
                    Log.i(TAG, "asked for tag: " + bytesToHexNpe(tal.getTag().getTagBytes()) + " default is too long, cut to: " + bytesToHexNpe(usedValue));
                } else if (defaultValue.length < lengthOfTag) {
                    // increase length
                    usedValue = new byte[lengthOfTag];
                    System.arraycopy(defaultValue, 0, usedValue, 0, defaultValue.length);
                    Log.i(TAG, "asked for tag: " + bytesToHexNpe(tal.getTag().getTagBytes()) + " default is too short, increased to: " + bytesToHexNpe(usedValue));
                } else {
                    // correct length
                    usedValue = defaultValue.clone();
                    Log.i(TAG, "asked for tag: " + bytesToHexNpe(tal.getTag().getTagBytes()) + " default found: " + bytesToHexNpe(usedValue));
                }
            } else {
                // defaultValue is null means the tag was not found in our tags database for default values
                usedValue = new byte[lengthOfTag];
                Log.i(TAG, "asked for tag: " + bytesToHexNpe(tal.getTag().getTagBytes()) + " NO default found, generate zeroed: " + bytesToHexNpe(usedValue));
            }
            // now usedValue does have the correct length
            sb.append(bytesToHexNpe(usedValue));
        }
        String constructedGpoString = sb.toString();
        String tagLength2d = bytesToHexNpe(intToByteArray(valueOfTagSum)); // length value
        String tagLength2dAnd2 = bytesToHexNpe(intToByteArray(valueOfTagSum + 2)); // length value + 2
        String constructedGpoCommandString = "80A80000" + tagLength2dAnd2 + "83" + tagLength2d + constructedGpoString + "00";
        return hexToBytes(constructedGpoCommandString);
    }

    /**
     * step 5 code end
    */

    /**
     * build a select apdu command
     *
     * @param data
     * @return
     */
    private byte[] selectApdu(@NonNull byte[] data) {
        byte[] commandApdu = new byte[6 + data.length];
        commandApdu[0] = (byte) 0x00;  // CLA
        commandApdu[1] = (byte) 0xA4;  // INS
        commandApdu[2] = (byte) 0x04;  // P1
        commandApdu[3] = (byte) 0x00;  // P2
        commandApdu[4] = (byte) (data.length & 0x0FF);       // Lc
        System.arraycopy(data, 0, commandApdu, 5, data.length);
        commandApdu[commandApdu.length - 1] = (byte) 0x00;  // Le
        return commandApdu;
    }

    /**
     * checks if the response has an 0x'9000' at the end means success
     * and the method returns the data without 0x'9000' at the end
     * if any other trailing bytes show up the method returns NULL
     * @param data
     * @return
     */
    private byte[] checkResponse(@NonNull byte[] data) {
        // simple sanity check
        if (data.length < 5) {
            return null;
        } // not ok
        int status = ((0xff & data[data.length - 2]) << 8) | (0xff & data[data.length - 1]);
        if (status != 0x9000) {
            return null;
        } else {
            return Arrays.copyOfRange(data, 0, data.length - 2);
        }
    }

    /**
     * section for conversion utils
     */

    /**
     * converts a byte array to a hex encoded string
     * This method is Null Pointer Exception (NPE) safe
     * @param bytes
     * @return hex encoded string
     */
    public static String bytesToHexNpe(byte[] bytes) {
        if (bytes != null) {
            StringBuffer result = new StringBuffer();
            for (byte b : bytes)
                result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            return result.toString();
        } else {
            return "";
        }
    }

    /**
     * converts a hex encoded string to a byte array
     * @param str
     * @return
     */
    public static byte[] hexToBytes(String str) {
        byte[] bytes = new byte[str.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(str.substring(2 * i, 2 * i + 2),
                    16);
        }
        return bytes;
    }

    /**
     * converts a byte to int
     * @param b
     * @return
     */
    public static int byteToInt(byte b) {
        return (int) b & 0xFF;
    }

    /**
     * converts an integer to a byte array
     * @param value
     * @return
     */
    public static byte[] intToByteArray(int value) {
        return new BigInteger(String.valueOf(value)).toByteArray();
    }

    /**
     * section for NFC
     */

    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mNfcAdapter != null) {
            if (!mNfcAdapter.isEnabled())
                showWirelessSettings();
            Bundle options = new Bundle();
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
            // Enable ReaderMode for all types of card and disable platform sounds
            // the option NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK is NOT set
            // to get the data of the tag afer reading
            mNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }
    }

    /**
     * important is the disabling of the ReaderMode when activity is pausing
     */

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null)
            mNfcAdapter.disableReaderMode(this);
    }

    /**
     * section for UI
     */

    /**
     * shows a progress bar as long as the reading lasts
     *
     * @param isVisible
     */

    private void setLoadingLayoutVisibility(boolean isVisible) {
        runOnUiThread(() -> {
            if (isVisible) {
                loadingLayout.setVisibility(View.VISIBLE);
            } else {
                loadingLayout.setVisibility(View.GONE);
            }
        });
    }

    /**
     * play a sound when reading is done
     */
    private void playPing() {
        /**
         * filename: conveniencestorering_96090.mp3
         * sound: ConvenienceStoreRing
         * created by: DataOperativeX
         * https://pixabay.com/sound-effects/id-96090/
         * Sound Effect from <a href="https://pixabay.com/sound-effects/?utm_source=link-attribution&amp;utm_medium=referral&amp;utm_campaign=music&amp;utm_content=96090">Pixabay</a>
         */
        //MediaPlayer mp = MediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI);
        //MediaPlayer mp = MediaPlayer.create(this, R.raw.conveniencestorering_96090);
        //MediaPlayer mp = MediaPlayer.create(this, R.raw.single_ping);

        // https://ringtonesdump.com/ping-ringtone.html
        /*
        filename: ping_ringtone.mp3
        Audio Size:	10 Kb
        Duration:
        Format:	mp3 / m4r
        Bitrate:	266 Kbps
        Sample rate:	44.1 khz
        Category:	Message
        Views:	1034
        Downloads:	366
        Licence:	Intended exclusively for private use
         */
        MediaPlayer mp = MediaPlayer.create(this, R.raw.ping_ringtone);
        mp.start();
    }


    /**
     * prints a nice header for each step
     *
     * @param step
     * @param message
     */
    private void printStepHeader(int step, String message) {
        // message should not extend 29 characters, longer messages will get trimmed
        String emptyMessage = "                                 ";
        StringBuilder sb = new StringBuilder();
        sb.append(outputString); // has already a line feed at the end
        sb.append("").append("\n");
        sb.append(stepSeparatorString).append("\n");
        sb.append("************ step ").append(String.format("%02d", step)).append(" ************").append("\n");
        sb.append("* ").append((message + emptyMessage).substring(0, 29)).append(" *").append("\n");
        sb.append(stepSeparatorString).append("\n");
        outputString = sb.toString();
    }

    /**
     * used for printing the card responses in a human readable format to a string
     *
     * @param responseData
     * @return
     */
    private String prettyPrintDataToString(byte[] responseData) {
        StringBuilder sb = new StringBuilder();
        sb.append("------------------------------------").append("\n");
        sb.append(trimLeadingLineFeeds(TlvUtil.prettyPrintAPDUResponse(responseData))).append("\n");
        sb.append("------------------------------------").append("\n");
        return sb.toString();
    }

    /**
     * trim leading line feeds if existing
     *
     * @param input
     * @return
     */
    public static String trimLeadingLineFeeds(String input) {
        String[] output = input.split("^\\n+", 2);
        return output.length > 1 ? output[1] : output[0];
    }

    private void clearData(final TextView textView) {
        runOnUiThread(() -> {
            outputString = "";
            exportString = "";
            textView.setText("");
        });
    }

    private void writeToUiAppend(String message) {
        outputString = outputString + message + "\n";
    }

    private void writeToUiFinal(final TextView textView) {
        if (textView == (TextView) etLog) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView.setText(outputString);
                    System.out.println(outputString); // print the data to console
                }
            });
        }
    }

    private void provideTextViewDataForExport(TextView textView) {
        exportString = textView.getText().toString();
    }

    private void writeToUiToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(),
                    message,
                    Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * section OptionsMenu export text file methods
     */

    private void exportTextFile() {
        provideTextViewDataForExport(etLog);
        if (exportString.isEmpty()) {
            writeToUiToast("Scan a tag first before writing files :-)");
            return;
        }
        writeStringToExternalSharedStorage();
    }

    private void writeStringToExternalSharedStorage() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Optionally, specify a URI for the file that should appear in the
        // system file picker when it loads.
        //boolean pickerInitialUri = false;
        //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);
        // get filename from edittext
        String filename = exportStringFileName;
        // sanity check
        if (filename.equals("")) {
            writeToUiToast("scan a tag before writing the content to a file :-)");
            return;
        }
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        selectTextFileActivityResultLauncher.launch(intent);
    }

    ActivityResultLauncher<Intent> selectTextFileActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        Intent resultData = result.getData();
                        // The result data contains a URI for the document or directory that
                        // the user selected.
                        Uri uri = null;
                        if (resultData != null) {
                            uri = resultData.getData();
                            // Perform operations on the document using its URI.
                            try {
                                // get file content from edittext
                                String fileContent = exportString;
                                System.out.println("## data to write: " + exportString);
                                writeTextToUri(uri, fileContent);
                                writeToUiToast("file written to external shared storage: " + uri.toString());
                            } catch (IOException e) {
                                e.printStackTrace();
                                writeToUiToast("ERROR: " + e.toString());
                                return;
                            }
                        }
                    }
                }
            });

    private void writeTextToUri(Uri uri, String data) throws IOException {
        try {
            System.out.println("** data to write: " + data);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(getApplicationContext().getContentResolver().openOutputStream(uri));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        } catch (IOException e) {
            System.out.println("Exception File write failed: " + e.toString());
        }
    }

    /**
     * section for OptionsMenu
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_basic, menu);

        MenuItem mCopyData = menu.findItem(R.id.action_copy_data);
        mCopyData.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(@NonNull MenuItem menuItem) {
                ClipboardManager clipboard = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("BasicNfcEmvReader", etLog.getText());
                clipboard.setPrimaryClip(clip);
                // show toast only on Android versions < 13
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                    Toast.makeText(getApplicationContext(), "copied", Toast.LENGTH_SHORT).show();
                return false;
            }
        });

        MenuItem mExportTextFile = menu.findItem(R.id.action_export_text_file);
        mExportTextFile.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Log.i(TAG, "mExportTextFile");
                exportTextFile();
                return false;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }
}