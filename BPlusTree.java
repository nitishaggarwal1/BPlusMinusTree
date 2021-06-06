// Searching on a B+ tree in Java

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

public class BPlusTree {
  int m;
  InternalNode root;
  LeafNode firstLeaf;

  // Binary search program
  private int binarySearch(DictionaryPair[] dps, int numPairs, int t) {
    Comparator<DictionaryPair> c = new Comparator<DictionaryPair>() {
      @Override
      public int compare(DictionaryPair o1, DictionaryPair o2) {
        Integer a = Integer.valueOf(o1.key);
        Integer b = Integer.valueOf(o2.key);
        return a.compareTo(b);
      }
    };
    byte[] bytes = null;
    return Arrays.binarySearch(dps, 0, numPairs, new DictionaryPair(t, bytes), c);
  }

  // Find the leaf node
  private LeafNode findLeafNode(int key) {

    Integer[] keys = this.root.keys;
    int i;

    for (i = 0; i < this.root.degree - 1; i++) {
      if (key < keys[i]) {
        break;
      }
    }

    Node child = this.root.childPointers[i];
    if (child instanceof LeafNode) {
      return (LeafNode) child;
    } else {
      return findLeafNode((InternalNode) child, key);
    }
  }

  // Find the leaf node
  private LeafNode findLeafNode(InternalNode node, int key) {

    Integer[] keys = node.keys;
    int i;

    for (i = 0; i < node.degree - 1; i++) {
      if (key < keys[i]) {
        break;
      }
    }
    Node childNode = node.childPointers[i];
    if (childNode instanceof LeafNode) {
      return (LeafNode) childNode;
    } else {
      return findLeafNode((InternalNode) node.childPointers[i], key);
    }
  }

  // Finding the index of the pointer
  private int findIndexOfPointer(Node[] pointers, LeafNode node) {
    int i;
    for (i = 0; i < pointers.length; i++) {
      if (pointers[i] == node) {
        break;
      }
    }
    return i;
  }

  // Get the mid point
  private int getMidpoint() {
    return (int) Math.ceil((this.m + 1) / 2.0) - 1;
  }

  // Balance the tree
  private void handleDeficiency(InternalNode in) {

    InternalNode sibling;
    InternalNode parent = in.parent;

    if (this.root == in) {
      for (int i = 0; i < in.childPointers.length; i++) {
        if (in.childPointers[i] != null) {
          if (in.childPointers[i] instanceof InternalNode) {
            this.root = (InternalNode) in.childPointers[i];
            this.root.parent = null;
          } else if (in.childPointers[i] instanceof LeafNode) {
            this.root = null;
          }
        }
      }
    }

    else if (in.leftSibling != null && in.leftSibling.isLendable()) {
      sibling = in.leftSibling;
    } else if (in.rightSibling != null && in.rightSibling.isLendable()) {
      sibling = in.rightSibling;

      int borrowedKey = sibling.keys[0];
      Node pointer = sibling.childPointers[0];

      in.keys[in.degree - 1] = parent.keys[0];
      in.childPointers[in.degree] = pointer;

      parent.keys[0] = borrowedKey;

      sibling.removePointer(0);
      Arrays.sort(sibling.keys);
      sibling.removePointer(0);
      shiftDown(in.childPointers, 1);
    } else if (in.leftSibling != null && in.leftSibling.isMergeable()) {

    } else if (in.rightSibling != null && in.rightSibling.isMergeable()) {
      sibling = in.rightSibling;
      sibling.keys[sibling.degree - 1] = parent.keys[parent.degree - 2];
      Arrays.sort(sibling.keys, 0, sibling.degree);
      parent.keys[parent.degree - 2] = null;

      for (int i = 0; i < in.childPointers.length; i++) {
        if (in.childPointers[i] != null) {
          sibling.prependChildPointer(in.childPointers[i]);
          in.childPointers[i].parent = sibling;
          in.removePointer(i);
        }
      }

      parent.removePointer(in);

      sibling.leftSibling = in.leftSibling;
    }

    if (parent != null && parent.isDeficient()) {
      handleDeficiency(parent);
    }
  }

  private boolean isEmpty() {
    return firstLeaf == null;
  }

  private int linearNullSearch(DictionaryPair[] dps) {
    for (int i = 0; i < dps.length; i++) {
      if (dps[i] == null) {
        return i;
      }
    }
    return -1;
  }

  private int linearNullSearch(Node[] pointers) {
    for (int i = 0; i < pointers.length; i++) {
      if (pointers[i] == null) {
        return i;
      }
    }
    return -1;
  }

  private void shiftDown(Node[] pointers, int amount) {
    Node[] newPointers = new Node[this.m + 1];
    for (int i = amount; i < pointers.length; i++) {
      newPointers[i - amount] = pointers[i];
    }
    pointers = newPointers;
  }

  private void sortDictionary(DictionaryPair[] dictionary) {
    Arrays.sort(dictionary, new Comparator<DictionaryPair>() {
      @Override
      public int compare(DictionaryPair o1, DictionaryPair o2) {
        if (o1 == null && o2 == null) {
          return 0;
        }
        if (o1 == null) {
          return 1;
        }
        if (o2 == null) {
          return -1;
        }
        return o1.compareTo(o2);
      }
    });
  }

  private Node[] splitChildPointers(InternalNode in, int split) {

    Node[] pointers = in.childPointers;
    Node[] halfPointers = new Node[this.m + 1];

    for (int i = split + 1; i < pointers.length; i++) {
      halfPointers[i - split - 1] = pointers[i];
      in.removePointer(i);
    }

    return halfPointers;
  }

  private DictionaryPair[] splitDictionary(LeafNode ln, int split) {

    DictionaryPair[] dictionary = ln.dictionary;

    DictionaryPair[] halfDict = new DictionaryPair[this.m];

    for (int i = split; i < dictionary.length; i++) {
      halfDict[i - split] = dictionary[i];
      ln.delete(i);
    }

    return halfDict;
  }

  private void splitInternalNode(InternalNode in) {

    InternalNode parent = in.parent;

    int midpoint = getMidpoint();
    int newParentKey = in.keys[midpoint];
    Integer[] halfKeys = splitKeys(in.keys, midpoint);
    Node[] halfPointers = splitChildPointers(in, midpoint);

    in.degree = linearNullSearch(in.childPointers);

    InternalNode sibling = new InternalNode(this.m, halfKeys, halfPointers);
    for (Node pointer : halfPointers) {
      if (pointer != null) {
        pointer.parent = sibling;
      }
    }

    sibling.rightSibling = in.rightSibling;
    if (sibling.rightSibling != null) {
      sibling.rightSibling.leftSibling = sibling;
    }
    in.rightSibling = sibling;
    sibling.leftSibling = in;

    if (parent == null) {

      Integer[] keys = new Integer[this.m];
      keys[0] = newParentKey;
      InternalNode newRoot = new InternalNode(this.m, keys);
      newRoot.appendChildPointer(in);
      newRoot.appendChildPointer(sibling);
      this.root = newRoot;

      in.parent = newRoot;
      sibling.parent = newRoot;

    } else {

      parent.keys[parent.degree - 1] = newParentKey;
      Arrays.sort(parent.keys, 0, parent.degree);

      int pointerIndex = parent.findIndexOfPointer(in) + 1;
      parent.insertChildPointer(sibling, pointerIndex);
      sibling.parent = parent;
    }
  }

  private Integer[] splitKeys(Integer[] keys, int split) {

    Integer[] halfKeys = new Integer[this.m];

    keys[split] = null;

    for (int i = split + 1; i < keys.length; i++) {
      halfKeys[i - split - 1] = keys[i];
      keys[i] = null;
    }

    return halfKeys;
  }

  public void insert(int key, byte[] value) {
    if (isEmpty()) {

      LeafNode ln = new LeafNode(this.m, new DictionaryPair(key, value));

      this.firstLeaf = ln;

    } else {
      LeafNode ln = (this.root == null) ? this.firstLeaf : findLeafNode(key);

      if (!ln.insert(new DictionaryPair(key, value))) {

        ln.dictionary[ln.numPairs] = new DictionaryPair(key, value);
        ln.numPairs++;
        sortDictionary(ln.dictionary);

        int midpoint = getMidpoint();
        DictionaryPair[] halfDict = splitDictionary(ln, midpoint);

        if (ln.parent == null) {

          Integer[] parent_keys = new Integer[this.m];
          parent_keys[0] = halfDict[0].key;
          InternalNode parent = new InternalNode(this.m, parent_keys);
          ln.parent = parent;
          parent.appendChildPointer(ln);

        } else {
          int newParentKey = halfDict[0].key;
          ln.parent.keys[ln.parent.degree - 1] = newParentKey;
          Arrays.sort(ln.parent.keys, 0, ln.parent.degree);
        }

        LeafNode newLeafNode = new LeafNode(this.m, halfDict, ln.parent);

        int pointerIndex = ln.parent.findIndexOfPointer(ln) + 1;
        ln.parent.insertChildPointer(newLeafNode, pointerIndex);

        newLeafNode.rightSibling = ln.rightSibling;
        if (newLeafNode.rightSibling != null) {
          newLeafNode.rightSibling.leftSibling = newLeafNode;
        }
        ln.rightSibling = newLeafNode;
        newLeafNode.leftSibling = ln;

        if (this.root == null) {

          this.root = ln.parent;

        } else {
          InternalNode in = ln.parent;
          while (in != null) {
            if (in.isOverfull()) {
              splitInternalNode(in);
            } else {
              break;
            }
            in = in.parent;
          }
        }
      }
    }
  }

  public byte[] search(int key, boolean printable) {

    if (isEmpty()) {
      return null;
    }

    LeafNode ln = (this.root == null) ? this.firstLeaf : findLeafNode(key);

    DictionaryPair[] dps = ln.dictionary;
    int index = binarySearch(dps, ln.numPairs, key);

    if (index < 0) {
      if(printable)
      System.out.println("Record " + key + " Not Found");
      return null;
    } else {
      if(printable)
      System.out.print("Record " + key + " Found : ");
      parseBytes(dps[index].value, printable);
      return dps[index].value;
    }
  }

  public ArrayList<byte[]> search(int lowerBound, int upperBound, boolean printable) {

    ArrayList<byte[]> values = new ArrayList<byte[]>();

    LeafNode currNode = this.firstLeaf;
    while (currNode != null) {

      DictionaryPair dps[] = currNode.dictionary;
      for (DictionaryPair dp : dps) {

        if (dp == null) {
          break;
        }

        if (lowerBound <= dp.key && dp.key <= upperBound) {
            if(printable)
            System.out.print("Record " + dp.key + " Found " + " : ");
            parseBytes(dp.value, printable);
            values.add(dp.value);
        }
      }
      currNode = currNode.rightSibling;

    }

    return values;
  }

  public BPlusTree(int m) {
    this.m = m;
    this.root = null;
  }

  public class Node {
    InternalNode parent;
  }

  private class InternalNode extends Node {
    int maxDegree;
    int minDegree;
    int degree;
    InternalNode leftSibling;
    InternalNode rightSibling;
    Integer[] keys;
    Node[] childPointers;

    private void appendChildPointer(Node pointer) {
      this.childPointers[degree] = pointer;
      this.degree++;
    }

    private int findIndexOfPointer(Node pointer) {
      for (int i = 0; i < childPointers.length; i++) {
        if (childPointers[i] == pointer) {
          return i;
        }
      }
      return -1;
    }

    private void insertChildPointer(Node pointer, int index) {
      for (int i = degree - 1; i >= index; i--) {
        childPointers[i + 1] = childPointers[i];
      }
      this.childPointers[index] = pointer;
      this.degree++;
    }

    private boolean isDeficient() {
      return this.degree < this.minDegree;
    }

    private boolean isLendable() {
      return this.degree > this.minDegree;
    }

    private boolean isMergeable() {
      return this.degree == this.minDegree;
    }

    private boolean isOverfull() {
      return this.degree == maxDegree + 1;
    }

    private void prependChildPointer(Node pointer) {
      for (int i = degree - 1; i >= 0; i--) {
        childPointers[i + 1] = childPointers[i];
      }
      this.childPointers[0] = pointer;
      this.degree++;
    }

    private void removeKey(int index) {
      this.keys[index] = null;
    }

    private void removePointer(int index) {
      this.childPointers[index] = null;
      this.degree--;
    }

    private void removePointer(Node pointer) {
      for (int i = 0; i < childPointers.length; i++) {
        if (childPointers[i] == pointer) {
          this.childPointers[i] = null;
        }
      }
      this.degree--;
    }

    private InternalNode(int m, Integer[] keys) {
      this.maxDegree = m;
      this.minDegree = (int) Math.ceil(m / 2.0);
      this.degree = 0;
      this.keys = keys;
      this.childPointers = new Node[this.maxDegree + 1];
    }

    private InternalNode(int m, Integer[] keys, Node[] pointers) {
      this.maxDegree = m;
      this.minDegree = (int) Math.ceil(m / 2.0);
      this.degree = linearNullSearch(pointers);
      this.keys = keys;
      this.childPointers = pointers;
    }
  }

  public class LeafNode extends Node {
    int maxNumPairs;
    int minNumPairs;
    int numPairs;
    LeafNode leftSibling;
    LeafNode rightSibling;
    DictionaryPair[] dictionary;

    public void delete(int index) {
      this.dictionary[index] = null;
      numPairs--;
    }

    public boolean insert(DictionaryPair dp) {
      if (this.isFull()) {
        return false;
      } else {
        this.dictionary[numPairs] = dp;
        numPairs++;
        Arrays.sort(this.dictionary, 0, numPairs);

        return true;
      }
    }

    public boolean isDeficient() {
      return numPairs < minNumPairs;
    }

    public boolean isFull() {
      return numPairs == maxNumPairs;
    }

    public boolean isLendable() {
      return numPairs > minNumPairs;
    }

    public boolean isMergeable() {
      return numPairs == minNumPairs;
    }

    public LeafNode(int m, DictionaryPair dp) {
      this.maxNumPairs = m - 1;
      this.minNumPairs = (int) (Math.ceil(m / 2) - 1);
      this.dictionary = new DictionaryPair[m];
      this.numPairs = 0;
      this.insert(dp);
    }

    public LeafNode(int m, DictionaryPair[] dps, InternalNode parent) {
      this.maxNumPairs = m - 1;
      this.minNumPairs = (int) (Math.ceil(m / 2) - 1);
      this.dictionary = dps;
      this.numPairs = linearNullSearch(dps);
      this.parent = parent;
    }
  }

  public class DictionaryPair implements Comparable<DictionaryPair> {
    int key;
    byte[] value;

    public DictionaryPair(int key, byte[] value) {
      this.key = key;
      this.value = value;
    }

    public int compareTo(DictionaryPair o) {
      if (key == o.key) {
        return 0;
      } else if (key > o.key) {
        return 1;
      } else {
        return -1;
      }
    }
  }

  public void parseBytes(byte[] Record, boolean printable){
     int numBytesInSdtnameField = constants.STD_NAME_SIZE;
     int numBytesIntField = Integer.BYTES;
     byte[] sdtnameBytes = new byte[numBytesInSdtnameField];
     byte[] idBytes = new byte[constants.ID_SIZE];
     byte[] dateBytes = new byte[constants.DATE_SIZE];
     byte[] yearBytes = new byte[constants.YEAR_SIZE];
     byte[] monthBytes = new byte[constants.MONTH_SIZE];
     byte[] mdateBytes = new byte[constants.MDATE_SIZE];
     byte[] dayBytes = new byte[constants.DAY_SIZE];
     byte[] timeBytes = new byte[constants.TIME_SIZE];
     byte[] sensorIdBytes = new byte[constants.SENSORID_SIZE];
     byte[] sensorNameBytes = new byte[constants.SENSORNAME_SIZE];
     byte[] countsBytes = new byte[constants.COUNTS_SIZE];
     SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");
     
     System.arraycopy(Record, 0, sdtnameBytes, 0, numBytesInSdtnameField);
     
     String sdtNameString = new String(sdtnameBytes);
                    
     System.arraycopy(Record, constants.ID_OFFSET, idBytes, 0, numBytesIntField);
     System.arraycopy(Record, constants.DATE_OFFSET, dateBytes, 0, constants.DATE_SIZE);
     System.arraycopy(Record, constants.YEAR_OFFSET, yearBytes, 0, numBytesIntField);
     System.arraycopy(Record, constants.MONTH_OFFSET, monthBytes, 0, constants.MONTH_SIZE);
     System.arraycopy(Record, constants.MDATE_OFFSET, mdateBytes, 0, numBytesIntField);
     System.arraycopy(Record, constants.DAY_OFFSET, dayBytes, 0, constants.DAY_SIZE);
     System.arraycopy(Record, constants.TIME_OFFSET, timeBytes, 0, numBytesIntField);
     System.arraycopy(Record, constants.SENSORID_OFFSET, sensorIdBytes, 0, numBytesIntField);
     System.arraycopy(Record, constants.SENSORNAME_OFFSET, sensorNameBytes, 0, constants.SENSORNAME_SIZE);
     System.arraycopy(Record, constants.COUNTS_OFFSET, countsBytes, 0, numBytesIntField);

     // Convert long data into Date object
     Date date = new Date(ByteBuffer.wrap(dateBytes).getLong());

     // Get a string representation of the record for printing to stdout
     String record = sdtNameString.trim() + "," + ByteBuffer.wrap(idBytes).getInt()
                                + "," + dateFormat.format(date) + "," + ByteBuffer.wrap(yearBytes).getInt() +
                                "," + new String(monthBytes).trim() + "," + ByteBuffer.wrap(mdateBytes).getInt()
                                + "," + new String(dayBytes).trim() + "," + ByteBuffer.wrap(timeBytes).getInt()
                                + "," + ByteBuffer.wrap(sensorIdBytes).getInt() + "," +
                                new String(sensorNameBytes).trim() + "," + ByteBuffer.wrap(countsBytes).getInt();
     if(printable)
     System.out.println(record);
  }
  
  public static void main(String[] args) throws IOException {
      
        // check for correct number of arguments
        if (args.length != constants.DBQUERY_ARG_COUNT) {
            System.out.println("Error: Incorrect number of arguments were input");
            return;
        }

        int sv1 = Integer.parseInt(args[constants.DBQUERY_ARG1]);
        int sv2 = Integer.parseInt(args[constants.DBQUERY_ARG2]);
        int rv1 = Integer.parseInt(args[constants.DBQUERY_ARG3]);
        int rv2 = Integer.parseInt(args[constants.DBQUERY_ARG4]);
        int sv3 = Integer.parseInt(args[constants.DBQUERY_ARG5]);
        int sv4 = Integer.parseInt(args[constants.DBQUERY_ARG6]);
        int rv3 = Integer.parseInt(args[constants.DBQUERY_ARG7]);
        int rv4 = Integer.parseInt(args[constants.DBQUERY_ARG8]);
        
        long startTime = 0;
        long finishTime = 0;
        
        startTime = System.nanoTime();
        
        BPlusTree bpt = null;
        bpt = new BPlusTree(1000);
        
        int record_id = 0;
        int recordSize = Integer.parseInt(args[constants.DBQUERY_PAGE_SIZE_ARG]);

        String datafile = "heap." + recordSize;
        int numBytesInOneRecord = constants.TOTAL_SIZE;
        int numBytesInSdtnameField = constants.STD_NAME_SIZE;
        int numRecordsPerPage = recordSize/numBytesInOneRecord;
        byte[] page = new byte[recordSize];
        FileInputStream inStream = null;

        try {
            inStream = new FileInputStream(datafile);
            int numBytesRead = 0;
            // Create byte arrays for each field
            byte[] sdtnameBytes = new byte[numBytesInSdtnameField];
            
            // until the end of the binary file is reached
            while ((numBytesRead = inStream.read(page)) != -1) {
                // Process each record in page
                for (int i = 0; i < numRecordsPerPage; i++) {
                    byte[] recordBytes = new byte[constants.TOTAL_SIZE];
                    // Copy record's SdtName (field is located at multiples of the total record byte length)
                    System.arraycopy(page, (i*numBytesInOneRecord), sdtnameBytes, 0, numBytesInSdtnameField);

                    // Check if field is empty; if so, end of all records found (packed organisation)
                    if (sdtnameBytes[0] == 0) {
                        // can stop checking records
                        break;
                    }

                    System.arraycopy(page, (i*numBytesInOneRecord), recordBytes, 0, constants.TOTAL_SIZE);
                    
                    record_id++;
                    bpt.insert(record_id, recordBytes);
                }
            }
        }
        catch (FileNotFoundException e) {
            System.err.println("File not found " + e.getMessage());
        }
        catch (IOException e) {
            System.err.println("IO Exception " + e.getMessage());
        }
        finally {

            if (inStream != null) {
                inStream.close();
            }
        }

        bpt.search(rv1, rv2, false);
        startTime = System.nanoTime();
        //equality search
        System.out.println("equality search with Fanout 1000");
        bpt.search(sv1, true);
        bpt.search(sv2, true);
        
        //range search
        System.out.println("range search with Fanout 1000");
        bpt.search(rv1, rv2, true);
        
        finishTime = System.nanoTime();
        
        long timeInMilliseconds = (finishTime - startTime)/(constants.MILLISECONDS_PER_SECOND/1000);
        System.out.println("Time taken: " + timeInMilliseconds + " ns");
        
        System.out.println();
        
        startTime = 0;
        finishTime = 0;
        
        startTime = System.nanoTime();
        
        bpt = null;
        bpt = new BPlusTree(200);
        
        record_id = 0;
        recordSize = Integer.parseInt(args[constants.DBQUERY_PAGE_SIZE_ARG]);

        datafile = "heap." + recordSize;
        numBytesInOneRecord = constants.TOTAL_SIZE;
        numBytesInSdtnameField = constants.STD_NAME_SIZE;
        numRecordsPerPage = recordSize/numBytesInOneRecord;
        page = new byte[recordSize];
        inStream = null;

        try {
            inStream = new FileInputStream(datafile);
            int numBytesRead = 0;
            // Create byte arrays for each field
            byte[] sdtnameBytes = new byte[numBytesInSdtnameField];
            
            // until the end of the binary file is reached
            while ((numBytesRead = inStream.read(page)) != -1) {
                // Process each record in page
                for (int i = 0; i < numRecordsPerPage; i++) {
                    byte[] recordBytes = new byte[constants.TOTAL_SIZE];
                    // Copy record's SdtName (field is located at multiples of the total record byte length)
                    System.arraycopy(page, (i*numBytesInOneRecord), sdtnameBytes, 0, numBytesInSdtnameField);

                    // Check if field is empty; if so, end of all records found (packed organisation)
                    if (sdtnameBytes[0] == 0) {
                        // can stop checking records
                        break;
                    }

                    System.arraycopy(page, (i*numBytesInOneRecord), recordBytes, 0, constants.TOTAL_SIZE);
                    
                    record_id++;
                    bpt.insert(record_id, recordBytes);
                }
            }
        }
        catch (FileNotFoundException e) {
            System.err.println("File not found " + e.getMessage());
        }
        catch (IOException e) {
            System.err.println("IO Exception " + e.getMessage());
        }
        finally {

            if (inStream != null) {
                inStream.close();
            }
        }

        //equality search
        System.out.println("equality search  with Fanout 200");
        bpt.search(sv3, true);
        bpt.search(sv4, true);
        
        //range search
        System.out.println("range search  with Fanout 200");
        bpt.search(rv3, rv4, true);
        
        finishTime = System.nanoTime();
        
        timeInMilliseconds = (finishTime - startTime)/(constants.MILLISECONDS_PER_SECOND/1000);
        System.out.println("Time taken: " + timeInMilliseconds + " ns");
  }
}