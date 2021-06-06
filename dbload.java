import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class dbload {

    /*
     * Loads data from an input csv into fixed-length records. Record fields are:
     * SDT_NAME field = 24 bytes, offset = 0
     * id field = 4 bytes, offset = 24
     * date field = 8 bytes, offset = 28
     * year field = 4 bytes, offset = 36
     * month field = 9 bytes, offset = 40
     * mdate field = 4 bytes, offset = 49
     * day field = 9 bytes, offset = 53
     * time field = 4 bytes, offset = 62
     * sensorid field = 4 bytes, offset = 66
     * sensorname field = 38 bytes, offset = 70
     * counts field = 4 bytes, offset = 108
     * end of record = 111 (inclusive)
     *
     * Outputs a binary file called heap.pagesize
     */
    public static void main(String[] args) throws IOException {

        // check for correct number of arguments
        if (args.length != constants.DBLOAD_ARG_COUNT) {
            System.out.println("Error: Incorrect number of arguments were input");
            return;
        }

        int pageSize = Integer.parseInt(args[constants.DBLOAD_PAGE_SIZE_ARG]);
        String datafile = args[constants.DATAFILE_ARG];
        String outputFileName = "heap." + pageSize;
        int numRecordsLoaded = 0;
        int numberOfPagesUsed = 0;
        long startTime = 0;
        long finishTime = 0;
        boolean exceptionOccurred = false;
        final int numBytesFixedLengthRecord = constants.TOTAL_SIZE;
        int numRecordsPerPage = pageSize/numBytesFixedLengthRecord;
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");

        BufferedReader reader = null;
        FileOutputStream outputStream = null;
        ByteArrayOutputStream byteOutputStream = null;
        DataOutputStream dataOutput = null;

        try {

            reader = new BufferedReader(new FileReader(datafile));
            outputStream = new FileOutputStream(outputFileName, true);
            byteOutputStream = new ByteArrayOutputStream();
            dataOutput = new DataOutputStream(byteOutputStream);

            startTime = System.nanoTime();

            // read in the header line (not processed further, as datafile fieldnames are known)
            String line = reader.readLine();

            // read in lines while not the end of file
            while ((line = reader.readLine()) != null) {

                String[] valuesAsStrings = line.split(",");

                // Convert data into relevant data types
                int id = Integer.parseInt(valuesAsStrings[constants.ID_POS]);
                String dateTimeString = valuesAsStrings[constants.DATETIME_POS];
                int year = Integer.parseInt(valuesAsStrings[constants.YEAR_POS]);
                String month = valuesAsStrings[constants.MONTH_POS];
                int mdate = Integer.parseInt(valuesAsStrings[constants.MDATE_POS]);
                String day = valuesAsStrings[constants.DAY_POS];
                int time = Integer.parseInt(valuesAsStrings[constants.TIME_POS]);
                String sensorIdString = valuesAsStrings[constants.SENSORID_POS];
                String sensorName = valuesAsStrings[constants.SENSORNAME_POS];
                int counts = Integer.parseInt(valuesAsStrings[constants.COUNTS_POS]);
                String sdtName = sensorIdString + dateTimeString;
                int sensorId = Integer.parseInt(sensorIdString);
                System.out.println(dateTimeString);
                // parse datetime field into a date object, then get long datatype representation
                Date date = dateFormat.parse(dateTimeString);
                long dateTimeLongRep = date.getTime();

                // Write bytes to data output stream
                dataOutput.writeBytes(getStringOfLength(sdtName, constants.STD_NAME_SIZE));
                dataOutput.writeInt(id);
                dataOutput.writeLong(dateTimeLongRep);
                dataOutput.writeInt(year);
                dataOutput.writeBytes(getStringOfLength(month, constants.MONTH_SIZE));
                dataOutput.writeInt(mdate);
                dataOutput.writeBytes(getStringOfLength(day, constants.DAY_SIZE));
                dataOutput.writeInt(time);
                dataOutput.writeInt(sensorId);
                dataOutput.writeBytes(getStringOfLength(sensorName, constants.SENSORNAME_SIZE));
                dataOutput.writeInt(counts);

                numRecordsLoaded++;
                // check if a new page is needed
                if (numRecordsLoaded % numRecordsPerPage == 0) {
                    dataOutput.flush();
                    // Get the byte array of loaded records, copy to an empty page and writeout
                    byte[] page = new byte[pageSize];
                    byte[] records = byteOutputStream.toByteArray();
                    int numberBytesToCopy = byteOutputStream.size();
                    System.arraycopy(records, 0, page, 0, numberBytesToCopy);
                    writeOut(outputStream, page);
                    numberOfPagesUsed++;
                    byteOutputStream.reset();
                }
            }

            // At end of csv, check if there are records in the current page to be written out
            if (numRecordsLoaded % numRecordsPerPage != 0) {
                dataOutput.flush();
                byte[] page = new byte[pageSize];
                byte[] records = byteOutputStream.toByteArray();
                int numberBytesToCopy = byteOutputStream.size();
                System.arraycopy(records, 0, page, 0, numberBytesToCopy);
                writeOut(outputStream, page);
                numberOfPagesUsed++;
                byteOutputStream.reset();
            }

            finishTime = System.nanoTime();
        }
        catch (FileNotFoundException e) {
            System.err.println("Error: File not present " + e.getMessage());
            exceptionOccurred = true;
        }
        catch (IOException e) {
            System.err.println("Error: IOExeption " + e.getMessage());
            exceptionOccurred = true;
        }
        catch (ParseException e) {
            System.err.println("Parse error when parsing date: " + e.getMessage());
        }
        finally {
            // close input/output streams
            if (reader != null) {
                reader.close();
            }
            if (dataOutput != null) {
                dataOutput.close();
            }
            if (byteOutputStream != null) {
                byteOutputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }

        // print out stats if all operations succeeded
        if (exceptionOccurred == false) {

            System.out.println("The number of records loaded: " + numRecordsLoaded);
            System.out.println("The number of pages used: " + numberOfPagesUsed);
            long timeInMilliseconds = (finishTime - startTime)/constants.MILLISECONDS_PER_SECOND;
            System.out.println("Time taken: " + timeInMilliseconds + " ms");
        }
    }

    // Writes out a byte array to file using a FileOutputStream
    public static void writeOut(FileOutputStream stream, byte[] byteArray)
            throws FileNotFoundException, IOException {

        stream.write(byteArray);
    }

    // Returns a whitespace padded string of the same length as parameter int length
    public static String getStringOfLength(String original, int length) {

        int lengthDiff = length - original.length();

        // Check difference in string lengths
        if (lengthDiff == 0) {
            return original;
        }
        else if (lengthDiff > 0) {
            // if original string is too short, pad end with whitespace
            StringBuilder string = new StringBuilder(original);
            for (int i = 0; i < lengthDiff; i++) {
                string.append(" ");
            }
            return string.toString();
        }
        else {
            // if original string is too long, shorten to required length
            return original.substring(0, length);
        }
    }
}