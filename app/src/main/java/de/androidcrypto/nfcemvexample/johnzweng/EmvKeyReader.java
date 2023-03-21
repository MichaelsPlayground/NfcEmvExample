package de.androidcrypto.nfcemvexample.johnzweng;

import static de.androidcrypto.nfcemvexample.johnzweng.EmvUtils.calculateSHA1;
import static de.androidcrypto.nfcemvexample.johnzweng.EmvUtils.getUnsignedBytes;

import fr.devnied.bitlib.BitUtils;
import fr.devnied.bitlib.BytesUtils;

import org.apache.commons.lang3.time.DateUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * @author Johannes Zweng (johannes@zweng.at) on 23.10.17.
 * source: https://github.com/johnzweng/android-emv-key-test/blob/master/app/src/main/java/at/zweng/emv/keys/EmvKeyReader.java
 */
public class EmvKeyReader {

    // EMV Book 2 (v4.3)
    // https://www.emvco.com/wp-content/uploads/2017/05/EMV_v4.3_Book_2_Security_and_Key_Management_20120607061923900.pdf

    /**
     * Parse the issuer public key.
     * See EMV (v4.3) Book 2, table 13 for Issuer public key certificate format.
     *
     * @param caPublicKey          Global card scheme (i.e. Mastercard, VISA, ..) CA public key
     * @param publicKeyCertificate issuer public key certificate as read from card
     * @param remainder            remaining modulus bytes as read from card
     * @param publicKeyExponent    public key exponent as read from card
     * @return the issuer public key
     */
    public IssuerIccPublicKey parseIssuerPublicKey(byte[] caPublicKeyExponent, byte[] caPublicKeyModulus, byte[] publicKeyCertificate,
                                                   byte[] remainder, byte[] publicKeyExponent)
            throws EmvParsingException {

        byte[] recoveredBytes = calculateRSA(publicKeyCertificate, caPublicKeyExponent,
                caPublicKeyModulus);
        final RecoveredIssuerPublicKey cert = this.parseIssuerPublicKeyCert(recoveredBytes,
                caPublicKeyModulus.length);
        Date expirationDate = parseDate(cert.certExpirationDate);
        if (cert.issuerPublicKeyExponentLength != publicKeyExponent.length) {
            throw new EmvParsingException(String.format("Issuer public key exponent has incorrect length. Should be %d"
                    + " but we got %d.", cert.issuerPublicKeyExponentLength, publicKeyExponent.length));
        }

        return new IssuerIccPublicKey(new BigInteger(1, publicKeyExponent),
                new BigInteger(1, concatenateModulus(cert.leftMostPubKeyDigits, remainder)),
                publicKeyCertificate, expirationDate);
    }

    /**
     * Check if cert is valid and if the calculated hash matches the hash in the certificate
     *
     * @param caPublicKey          used public key of card-system Root CA
     * @param publicKeyCertificate issuer public key cert as read from card
     * @param remainingBytes       remaining bytes of issuer public key as read from card
     * @param publicKeyExponent    exponent of issuer public key as read from card
     * @return true if validation is successful, false otherwise
     * @throws EmvParsingException
     */
    public boolean validateIssuerPublicKey(byte[] caPublicKeyExponent, byte[] caPublicKeyModulus, byte[] publicKeyCertificate,
                                           byte[] remainingBytes, byte[] publicKeyExponent) throws EmvParsingException {
        byte[] recoveredBytes = calculateRSA(publicKeyCertificate, caPublicKeyExponent,
                caPublicKeyModulus);
        final RecoveredIssuerPublicKey cert = this.parseIssuerPublicKeyCert(recoveredBytes,
                caPublicKeyModulus.length);

        ByteArrayOutputStream hashStream = new ByteArrayOutputStream();
        // calculate our own hash for comparison:
        hashStream.write((byte) cert.certificateFormat);
        hashStream.write(cert.issuerIdentifier, 0, cert.issuerIdentifier.length);
        hashStream.write(cert.certExpirationDate, 0, cert.certExpirationDate.length);
        hashStream.write(cert.certSerialNumber, 0, cert.certSerialNumber.length);
        hashStream.write((byte) cert.hashAlgoIndicator);
        hashStream.write((byte) cert.issuerPubKeyAlgoIndicator);
        hashStream.write((byte) cert.issuerPublicKeyLength);
        hashStream.write((byte) cert.issuerPublicKeyExponentLength);
        hashStream.write(cert.leftMostPubKeyDigits, 0, cert.leftMostPubKeyDigits.length);
        if (cert.optionalPadding.length > 0) {
            hashStream.write(cert.optionalPadding, 0, cert.optionalPadding.length);
        }
        if (remainingBytes != null && remainingBytes.length > 0) {
            hashStream.write(remainingBytes, 0, remainingBytes.length);
        }
        hashStream.write(publicKeyExponent, 0, publicKeyExponent.length);
        // calculate hash:
        byte[] calculatedHash = calculateSHA1(hashStream.toByteArray());
        // compare it with value in cert:
        return Arrays.equals(calculatedHash, cert.hashResult);
    }

    /**
     * Parse the issuer public key.
     * See EMV (v4.3) Book 2, table 13 for Issuer public key certificate format.
     *
     * @param issuerPublicKey         public key of card issuer
     * @param iccPublicKeyCertificate ICC public key certificate as read from card
     * @param iccRemainder            ICC remaining modulus bytes as read from card
     * @param iccPublicKeyExponent    ICC public key exponent as read from card
     * @return the ICC public key
     */
    public IssuerIccPublicKey parseIccPublicKey(byte[] issuerPublicKeyExponent, byte[] issuerPublicKeyModulus, byte[] iccPublicKeyCertificate,
                                                byte[] iccRemainder, byte[] iccPublicKeyExponent)
            throws EmvParsingException {
        byte[] recoveredBytes = calculateRSA(iccPublicKeyCertificate, issuerPublicKeyExponent,
                issuerPublicKeyModulus);
        final RecoveredIccPublicKey cert = this.parseIccPublicKeyCert(recoveredBytes,
                issuerPublicKeyModulus.length);
        Date expirationDate = parseDate(cert.certExpirationDate);
        if (cert.iccPublicKeyExponentLength != iccPublicKeyExponent.length) {
            throw new EmvParsingException(String.format("ICC public key exponent has incorrect length. Should be %d"
                    + " but we got %d.", cert.iccPublicKeyExponentLength, iccPublicKeyExponent.length));
        }

        return new IssuerIccPublicKey(new BigInteger(1, iccPublicKeyExponent),
                new BigInteger(1, concatenateModulus(cert.leftMostPubKeyDigits, iccRemainder)),
                iccPublicKeyCertificate, expirationDate);
    }

    /**
     * Check if cert is valid and if the calculated hash matches the hash in the certificate
     * TODO: this will fail in current implementation (missing lots of data to hash)
     *
     * @param issuerPublicKey         public key of card issuer
     * @param iccPublicKeyCertificate ICC public key certificate as read from card
     * @param iccRemainingBytes       ICC remaining modulus bytes as read from card
     * @param iccPublicKeyExponent    ICC public key exponent as read from card
     * @return true if validation is successful, false otherwise
     * @throws EmvParsingException
     */
    public boolean validateIccPublicKey(byte[] issuerPublicKeyExponent, byte[] issuerPublicKeyModulus, byte[] iccPublicKeyCertificate,
                                        byte[] iccRemainingBytes, byte[] iccPublicKeyExponent) throws EmvParsingException {
        byte[] recoveredBytes = calculateRSA(iccPublicKeyCertificate, issuerPublicKeyExponent,
                issuerPublicKeyModulus);
        final RecoveredIccPublicKey cert = this.parseIccPublicKeyCert(recoveredBytes,
                issuerPublicKeyModulus.length);

        ByteArrayOutputStream hashStream = new ByteArrayOutputStream();
        // calculate our own hash for comparison:
        hashStream.write((byte) cert.certificateFormat);
        hashStream.write(cert.applicationPan, 0, cert.applicationPan.length);
        hashStream.write(cert.certExpirationDate, 0, cert.certExpirationDate.length);
        hashStream.write(cert.certSerialNumber, 0, cert.certSerialNumber.length);
        hashStream.write((byte) cert.hashAlgoIndicator);
        hashStream.write((byte) cert.iccPubKeyAlgoIndicator);
        hashStream.write((byte) cert.iccPublicKeyLength);
        hashStream.write((byte) cert.iccPublicKeyExponentLength);
        hashStream.write(cert.leftMostPubKeyDigits, 0, cert.leftMostPubKeyDigits.length);
        if (cert.optionalPadding.length > 0) {
            hashStream.write(cert.optionalPadding, 0, cert.optionalPadding.length);
        }
        if (iccRemainingBytes != null && iccRemainingBytes.length > 0) {
            hashStream.write(iccRemainingBytes, 0, iccRemainingBytes.length);
        }
        hashStream.write(iccPublicKeyExponent, 0, iccPublicKeyExponent.length);
        // TODO FIX: validation currently will fail as a lot of more data (all fields for SDA) needs to be hashed
        // Quote EMV book 2: "and the static data to be authenticated specified in section 10.3 of Book 3"
        // This means we would need to hash ALL SFI contents which are marked for offline data authentication
        // in the AFL.

        // calculate hash:
        byte[] calculatedHash = calculateSHA1(hashStream.toByteArray());
        // compare it with value in cert:
        return Arrays.equals(calculatedHash, cert.hashResult);
    }


    /**
     * https://www.emvco.com/wp-content/uploads/2017/05/EMV_v4.3_Book_2_Security_and_Key_Management_20120607061923900.pdf
     * <p>
     * EMV spec 4.3, Book 2, table 13:
     * "Format of Data Recovered from Issuer Public Key Certificate":
     * <p>
     * Field Name                         Length   Description
     * Recovered Data Header                 1     Hex value '6A'
     * Certificate Format                    1     Hex value '02'
     * Issuer Identifier                     4     Leftmost 3-8 digits from the PAN (padded to the right with
     * Hex 'F's)
     * Certificate Expiration Date           2     MMYY after which this certificate is invalid (BCD format)
     * Certificate Serial Number             3     Binary number unique to this certificate assigned by the CA
     * Hash Algorithm Indicator              1     Identifies the hash algorithm used to produce the Hash Result
     * in the digital signature scheme (only 0x01 = SHA-1 allowed)
     * Issuer Public Key Algorithm Indicator 1     Identifies the digital signature algorithm
     * (only 0x01 = RSA allowed)
     * Issuer Public Key Length              1     length of the Issuer Public Key Modulus in bytes
     * Issuer Public Key Exponent Length     1     length of the Issuer Public Key Exponent in bytes
     * Issuer Public Key or Leftmost
     * Digits of the Issuer Public Key    nCA–36   If nI ≤ nCA – 36, consists of the full Issuer Public Key padded
     * to the right with nCA–36–nI bytes of value 'BB' If nI > nCA – 36,
     * consists of the nCA – 36 most significant bytes of the Issuer Public Key
     * Hash Result                           20    Hash of the Issuer Public Key and its related information
     * Recovered Data Trailer                1     Hex value 'BC' b
     */
    public class RecoveredIssuerPublicKey {
        int recoveredDataHeader;
        int certificateFormat;
        byte[] issuerIdentifier;
        byte[] certExpirationDate;
        byte[] certSerialNumber;
        int hashAlgoIndicator;
        int issuerPubKeyAlgoIndicator;
        int issuerPublicKeyLength;
        int issuerPublicKeyExponentLength;
        byte[] leftMostPubKeyDigits;
        byte[] optionalPadding;
        byte[] hashResult;
        int dataTrailer;

        public String dump() {
            StringBuilder sb = new StringBuilder();
            sb.append("Recovered Data Header: ").append(recoveredDataHeader).append("\n");
            sb.append("Certificate Format: ").append(certificateFormat).append("\n");
            sb.append("Issuer Identifier : ").append(bytesToHexNpe(issuerIdentifier)).append("\n");
            sb.append("Certificate Expiration Date: ").append(bytesToHexNpe(certExpirationDate)).append("\n");
            sb.append("Certificate Serial Number: ").append(bytesToHexNpe(certSerialNumber)).append("\n");
            sb.append("Hash Algorithm Indicator").append(hashAlgoIndicator).append("\n");
            sb.append("Issuer Public Key Algorithm Indicator: ").append(issuerPubKeyAlgoIndicator).append("\n");
            sb.append("Issuer Public Key Length: ").append(issuerPublicKeyLength).append("\n");
            sb.append("Issuer Public Key Exponent Length: ").append(issuerPublicKeyExponentLength).append("\n");
            sb.append("Leftmost Digits of the Issuer Public Key: ").append(bytesToHexNpe(leftMostPubKeyDigits)).append("\n");
            sb.append("Optional padding: ").append(bytesToHexNpe(optionalPadding)).append("\n");
            sb.append("Hash Result : ").append(bytesToHexNpe(hashResult)).append("\n");
            sb.append("dataTrailer: ").append(dataTrailer).append("\n");
            return sb.toString();

        }
        /*
         * Field Name                         Length   Description
         * Recovered Data Header                 1     Hex value '6A'
         * Certificate Format                    1     Hex value '02'
         * Issuer Identifier                     4     Leftmost 3-8 digits from the PAN (padded to the right with
         * Hex 'F's)
         * Certificate Expiration Date           2     MMYY after which this certificate is invalid (BCD format)
         * Certificate Serial Number             3     Binary number unique to this certificate assigned by the CA
         * Hash Algorithm Indicator              1     Identifies the hash algorithm used to produce the Hash Result
         * in the digital signature scheme (only 0x01 = SHA-1 allowed)
         * Issuer Public Key Algorithm Indicator 1     Identifies the digital signature algorithm
         * (only 0x01 = RSA allowed)
         * Issuer Public Key Length              1     length of the Issuer Public Key Modulus in bytes
         * Issuer Public Key Exponent Length     1     length of the Issuer Public Key Exponent in bytes
         * Issuer Public Key or Leftmost
         * Digits of the Issuer Public Key    nCA–36   If nI ≤ nCA – 36, consists of the full Issuer Public Key padded
         * to the right with nCA–36–nI bytes of value 'BB' If nI > nCA – 36,
         * consists of the nCA – 36 most significant bytes of the Issuer Public Key
         * Hash Result                           20    Hash of the Issuer Public Key and its related information
         * Recovered Data Trailer                1     Hex value 'BC' b
         */
    }


    /**
     * Parse the recovered issuer public key certificate bytes.
     *
     * @param recoveredBytes  recovered bytes after RSA
     * @param caModulusLength length of CA pubkey modulus in bytes
     * @return parsed data
     * @throws EmvParsingException
     */
    private RecoveredIssuerPublicKey parseIssuerPublicKeyCert(byte[] recoveredBytes, int caModulusLength)
            throws EmvParsingException {
        RecoveredIssuerPublicKey r = new RecoveredIssuerPublicKey();
        BitUtils bits = new BitUtils(recoveredBytes);

        r.recoveredDataHeader = bits.getNextInteger(8);
        if (r.recoveredDataHeader != 0x6a) {
            throw new EmvParsingException("Certificate started with incorrect header: "
                    + Integer.toHexString(r.recoveredDataHeader));
        }
        r.certificateFormat = bits.getNextInteger(8);
        if (r.certificateFormat != 0x02) {
            throw new EmvParsingException("Certificate Format is unknown: " + Integer.toHexString(r.certificateFormat));
        }
        r.issuerIdentifier = bits.getNextByte(32);
        r.certExpirationDate = bits.getNextByte(16);
        r.certSerialNumber = bits.getNextByte(24);
        // as of EMV 4.3 spec only "0x01" (= SHA-1) is specified
        r.hashAlgoIndicator = bits.getNextInteger(8);
        if (r.hashAlgoIndicator != 0x01) {
            throw new EmvParsingException("Hash Algorithm Indicator is invalid. Only 0x01 is allowed. We found: "
                    + Integer.toHexString(r.hashAlgoIndicator));
        }
        // as of EMV 4.3 spec only "0x01" (= RSA) is specified
        r.issuerPubKeyAlgoIndicator = bits.getNextInteger(8);
        if (r.issuerPubKeyAlgoIndicator != 0x01) {
            throw new EmvParsingException("Issuer Public Key Algorithm Indicator is invalid. Only 0x01 is allowed. "
                    + "We found: " + Integer.toHexString(r.issuerPubKeyAlgoIndicator));
        }
        r.issuerPublicKeyLength = bits.getNextInteger(8);
        r.issuerPublicKeyExponentLength = bits.getNextInteger(8);
        // johnzweng: according to EMV book 2 length of modulus bytes is length of CA modulus - 36
        // CA modulus length must be the same length as this certificate (this is property of RSA)
        int numberOfModulusBytesInCert = caModulusLength - 36;
        int paddingLength = 0; // # of padding bytes if nC<nCA-36
        if (r.issuerPublicKeyLength < numberOfModulusBytesInCert) {
            // in this case we have padding bytes, store the number of padding bytes
            paddingLength = numberOfModulusBytesInCert - r.issuerPublicKeyLength;
            numberOfModulusBytesInCert = r.issuerPublicKeyLength;
        }

        r.leftMostPubKeyDigits = bits.getNextByte(numberOfModulusBytesInCert * 8);
        // if we have padding bytes, skip them (not used)
        if (paddingLength > 0) {
            r.optionalPadding = bits.getNextByte(paddingLength * 8);
        } else {
            r.optionalPadding = new byte[0];
        }
        r.hashResult = bits.getNextByte(20 * 8);
        r.dataTrailer = bits.getNextInteger(8);
        if (r.dataTrailer != 0xbc) {//Trailer
            throw new EmvParsingException("Certificate ended with incorrect trailer: " +
                    Integer.toHexString(r.dataTrailer));
        }
        if (bits.getCurrentBitIndex() != bits.getSize()) {
            throw new EmvParsingException("There are bytes left in certificate after we have read all data.");
        }
        return r;
    }


    /**
     * https://www.emvco.com/wp-content/uploads/2017/05/EMV_v4.3_Book_2_Security_and_Key_Management_20120607061923900.pdf
     * <p>
     * EMV spec 4.3, Book 2, table 14:
     * "Format of Data Recovered from ICC Public Key Certificate ":
     * <p>
     * Field Name                         Length   Description
     * Recovered Data Header                 1     Hex value '0x6A'
     * Certificate Format                    1     Hex value '0x04'
     * Application PAN                      10     PAN padded to the right with FF's
     * Certificate Expiration Date           2     MMYY after which this certificate is invalid (BCD format)
     * Certificate Serial Number             3     Binary number unique to this certificate assigned by the CA
     * Hash Algorithm Indicator              1     Identifies the hash algorithm used to produce the Hash Result
     * in the digital signature scheme (only 0x01 = SHA-1 allowed)
     * ICC Public Key Algorithm Indicator    1     Identifies the digital signature algorithm
     * (only 0x01 = RSA allowed)
     * ICC Public Key Length                 1     length of the ICC Public Key Modulus in bytes
     * ICC Public Key Exponent Length        1     length of the ICC Public Key Exponent in bytes
     * ICC Public Key or Leftmost
     * Digits of the Issuer Public Key    nI–42   If nICC ≤ nI – 42, consists of the full Issuer Public Key padded
     * to the right with 'BB's, If nICC > nI – 42, consists of the nI – 42 most significant bytes of the ICC Public Key
     * Hash Result                           20    Hash of the Issuer Public Key and its related information
     * Recovered Data Trailer                1     Hex value 'BC' b
     */
    private class RecoveredIccPublicKey {
        int recoveredDataHeader;
        int certificateFormat;
        byte[] applicationPan;
        byte[] certExpirationDate;
        byte[] certSerialNumber;
        int hashAlgoIndicator;
        int iccPubKeyAlgoIndicator;
        int iccPublicKeyLength;
        int iccPublicKeyExponentLength;
        byte[] leftMostPubKeyDigits;
        byte[] optionalPadding;
        byte[] hashResult;
        int dataTrailer;


    }

    /**
     * Parse the recovered issuer public key certificate bytes.
     *
     * @param recoveredBytes         recovered bytes after RSA
     * @param issuerKeyModulusLength length of issuer pubkey modulus in bytes
     * @return parsed data
     * @throws EmvParsingException
     */
    private RecoveredIccPublicKey parseIccPublicKeyCert(byte[] recoveredBytes, int issuerKeyModulusLength)
            throws EmvParsingException {
        RecoveredIccPublicKey r = new RecoveredIccPublicKey();
        BitUtils bits = new BitUtils(recoveredBytes);

        r.recoveredDataHeader = bits.getNextInteger(8);
        if (r.recoveredDataHeader != 0x6a) {
            throw new EmvParsingException("Certificate started with incorrect header: "
                    + Integer.toHexString(r.recoveredDataHeader));
        }
        r.certificateFormat = bits.getNextInteger(8);
        if (r.certificateFormat != 0x04) {
            throw new EmvParsingException("Certificate Format is unknown: " + Integer.toHexString(r.certificateFormat));
        }
        r.applicationPan = bits.getNextByte(80);
        r.certExpirationDate = bits.getNextByte(16);
        r.certSerialNumber = bits.getNextByte(24);
        // as of EMV 4.3 spec only "0x01" (= SHA-1) is specified
        r.hashAlgoIndicator = bits.getNextInteger(8);
        if (r.hashAlgoIndicator != 0x01) {
            throw new EmvParsingException("Hash Algorithm Indicator is invalid. Only 0x01 is allowed. We found: "
                    + Integer.toHexString(r.hashAlgoIndicator));
        }
        // as of EMV 4.3 spec only "0x01" (= RSA) is specified
        r.iccPubKeyAlgoIndicator = bits.getNextInteger(8);
        if (r.iccPubKeyAlgoIndicator != 0x01) {
            throw new EmvParsingException("ICC Public Key Algorithm Indicator is invalid. Only 0x01 is allowed. "
                    + "We found: " + Integer.toHexString(r.iccPubKeyAlgoIndicator));
        }
        r.iccPublicKeyLength = bits.getNextInteger(8);
        r.iccPublicKeyExponentLength = bits.getNextInteger(8);
        // johnzweng: according to EMV book 2 length of modulus bytes is length of issuer modulus - 42
        // Issuer modulus length must be the same length as this certificate (this is a property of RSA)
        int numberOfModulusBytesInCert = issuerKeyModulusLength - 42;
        int paddingLength = 0; // # of padding bytes if nC<nCA-36
        if (r.iccPublicKeyLength < numberOfModulusBytesInCert) {
            // in this case we have padding bytes, store the number of padding bytes
            paddingLength = numberOfModulusBytesInCert - r.iccPublicKeyLength;
            numberOfModulusBytesInCert = r.iccPublicKeyLength;
        }

        r.leftMostPubKeyDigits = bits.getNextByte(numberOfModulusBytesInCert * 8);
        // if we have padding bytes, skip them (not used)
        if (paddingLength > 0) {
            r.optionalPadding = bits.getNextByte(paddingLength * 8);
        } else {
            r.optionalPadding = new byte[0];
        }
        r.hashResult = bits.getNextByte(20 * 8);
        r.dataTrailer = bits.getNextInteger(8);
        if (r.dataTrailer != 0xbc) {//Trailer
            throw new EmvParsingException("Certificate ended with incorrect trailer: " +
                    Integer.toHexString(r.dataTrailer));
        }
        if (bits.getCurrentBitIndex() != bits.getSize()) {
            throw new EmvParsingException("There are bytes left in certificate after we have read all data.");
        }
        return r;
    }


    /**
     * Either just returns leftmost digits or concatenated with remainder.
     *
     * @param leftMostDigits
     * @param remainder
     * @return
     */
    private static byte[] concatenateModulus(byte[] leftMostDigits, byte[] remainder) {
        final byte[] completeModulus;
        if (remainder != null && remainder.length > 0) {
            // concatenate the leftmost part of modulus (recovered from certificate) plus
            // the remainder bytes
            completeModulus = new byte[leftMostDigits.length + remainder.length];
            System.arraycopy(leftMostDigits, 0,
                    completeModulus, 0, leftMostDigits.length);
            System.arraycopy(remainder, 0,
                    completeModulus, leftMostDigits.length, remainder.length);
        } else {
            completeModulus = new byte[leftMostDigits.length];
            System.arraycopy(leftMostDigits, 0,
                    completeModulus, 0, leftMostDigits.length);
        }
        return completeModulus;
    }


    /**
     * Parse date value from 2 byte value
     *
     * @param dateBytes 2 bytes, containing YYMM as BCD-formatted digits
     * @return parsed date value
     * @throws EmvParsingException
     */
    private static Date parseDate(byte[] dateBytes) throws EmvParsingException {
        if (dateBytes == null || dateBytes.length != 2) {
            throw new EmvParsingException("Date value must be exact 2 bytes long.");
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMyy", Locale.getDefault());
        try {
            Date parsedDate = sdf.parse(BytesUtils.bytesToStringNoSpace(dateBytes));
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(parsedDate);
            int lastDayOfExpiryMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            return DateUtils.setDays(parsedDate, lastDayOfExpiryMonth);
        } catch (ParseException e) {
            throw new EmvParsingException("Unparsable date: " + BytesUtils.bytesToStringNoSpace(dateBytes), e);
        }
    }


    /**
     * Manually perform RSA operation: data ^ exponent mod modulus
     *
     * @param data     data bytes to operate on
     * @param exponent exponent
     * @param modulus  modulus
     * @return data ^ exponent mod modulus
     */
    private static byte[] calculateRSA(byte[] data, BigInteger exponent, BigInteger modulus) throws EmvParsingException {
        // bigInts here are unsigned:
        BigInteger dataBigInt = new BigInteger(1, data);

        return getUnsignedBytes(dataBigInt.modPow(exponent, modulus));
    }

    /**
     * Manually perform RSA operation: data ^ exponent mod modulus
     *
     * @param data                data bytes to operate on
     * @param caPublicKeyExponent exponent
     * @param caPublicKeyModulus  modulus
     * @return data ^ exponent mod modulus
     */
    private static byte[] calculateRSA(byte[] data, byte[] caPublicKeyExponent, byte[] caPublicKeyModulus) throws EmvParsingException {
        BigInteger exponent = new BigInteger(caPublicKeyExponent);
        BigInteger modulus = new BigInteger(caPublicKeyModulus);
        // bigInts here are unsigned:
        BigInteger dataBigInt = new BigInteger(1, data);
        return getUnsignedBytes(dataBigInt.modPow(exponent, modulus));
    }

    /**
     * converts a byte array to a hex encoded string
     *
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

}
