package BigT;

import btree.BTreeFile;
import btree.StringKey;
import btree.IntegerKey;
import global.*;
import index.IndexException;
import index.IndexScan;
import index.UnknownIndexTypeException;
import iterator.*;
import diskmgr.*;
import heap.*;
import java.util.*;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This class will be similar to heap.Scan, however, will provide different
 * types of accesses to the bigtable
 */
public class Stream implements GlobalConst {

    private int nscan = 0;
    /** in-core copy (pinned) of the same */
    private bigt _bigTable;
    /** PageId of current directory page (which is itself an HFPage) */
    private PageId dirpageId = new PageId();
    /** pageId of bigT */
    private PageId datapageId = new PageId();
    /** pointer to in-core data of dirpageId (page is pinned) */
    private HFPage dirpage = new HFPage();
    /**
     * record ID of the DataPageInfo struct (in the directory page) which describes
     * the data page where our current record lives.
     *
     */
    private MID datapageMid = new MID();
    /** Status of next user status */
    private boolean nextUserStatus;
    /** in-core copy (pinned) of the same */
    private HFPage datapage = new HFPage();

    /** record ID of the current record (from the current data page) */
    private MID userrid = new MID();
    /** The amount of pages available for sorting */
    int SORTPGNUM = 12;
    /**
     * Strings are set to use size 62 bytes, 2 extra bytes are added when setting
     * hdr for maps, hence 64
     */
    private short RECLENGTH = 62;

    private final static boolean OK = true;
    private IndexScan iscan = null;

    /**
     * Initialize a stream of maps on bigtable.
     * 
     * @param bigtable
     * @param orderType
     * @param rowFilter
     * @param columnFilter
     * @param valueFilter
     * @throws UnknownIndexTypeException
     * @throws InvalidTypeException
     * @throws IndexException
     */
    public Stream(bigt bigtable, int orderType, java.lang.String rowFilter, java.lang.String columnFilter,
            java.lang.String valueFilter) throws InvalidTupleSizeException, IOException, IndexException,
            InvalidTypeException, UnknownIndexTypeException {

        // System.out.println("Table name in sysdefs: " + SystemDefs.JavabaseDB.table.name);
        // System.out.println("Table name in stream: " + bigtable.name);

        _bigTable = bigtable;
        /** copy data about first directory page */

        // dirpageId.pid = _bigTable.hf._firstDirPageId.pid;
        nextUserStatus = true;

        AttrType[] attrType = new AttrType[4];
        attrType[0] = new AttrType(AttrType.attrString);
        attrType[1] = new AttrType(AttrType.attrString);
        attrType[2] = new AttrType(AttrType.attrInteger);
        attrType[3] = new AttrType(AttrType.attrString);
        short[] attrSize = new short[3];
        attrSize[0] = RECLENGTH;
        attrSize[1] = RECLENGTH;
        attrSize[2] = RECLENGTH;

        // create empty map we will use for reading data
        Map m = new Map();
        try {
            // set the header info for the new map
            m.setHdr((short) 4, attrType, attrSize);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        int size = m.size();

        // create an iterator by open a file scan
        FldSpec[] projlist = new FldSpec[4];
        RelSpec rel = new RelSpec(RelSpec.outer);
        projlist[0] = new FldSpec(rel, 1);
        projlist[1] = new FldSpec(rel, 2);
        projlist[2] = new FldSpec(rel, 3);
        projlist[3] = new FldSpec(rel, 4);

        // OutFilter for limiting results from BigTable search
        CondExpr[] outFilter = queryFilter(rowFilter, columnFilter, valueFilter);

        FileScan fscan = null;

        System.out.println("rowfilter: " + rowFilter + " colfilter: " + columnFilter + " valfilter: " + valueFilter);

        try {
            System.out.println("HFName: " + bigtable.name);
            System.out.println("sysdef DBname: " + SystemDefs.JavabaseDBName);
            fscan = new FileScan(bigtable.name, attrType, attrSize, (short) 4, 4, projlist, outFilter[0] == null ? null : outFilter );
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        int count = 0;
        m = null;
        MID mid = new MID();

        try {
            m = fscan.get_next();
            if(m == null)
                System.out.println("Stream.java: 133: FScan no map");
            else
                System.out.println("stream.java: 135: collabel: " + m.getColumnLabel());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        BTreeFile btf = null;

        boolean flag = true;

        // Sort "test1.in"
        Sort sort = null;
        try {
            String key = null;
            MID MID = new MID();
            Map temp = null;
            //TODO
            //sort = new Sort(attrType, (short) 4, attrSize, fscan, 1, order[0], 64, SORTPGNUM);
            switch (orderType){
                case OrderType.type1:
                    //results ordered by rowLabel then columnLabel then time stamp
                    try {
                        //TODO make sure key size when making btree is correct
                        btf = new BTreeFile("StreamOrderIndex", AttrType.attrString, STR_LEN*3, 1/*delete*/);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        Runtime.getRuntime().exit(1);
                    }
                    while ( temp != null) {
                        try {
                            key = m.getRowLabel() + " " + m.getColumnLabel() + " " + m.getTimeStamp();
                            mid = fscan._mid;
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            btf.insert(new StringKey(key), mid);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            temp = fscan.get_next();
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case OrderType.type2:
                    //ordered columnLabel, rowLabel, timestamp
                    try {
                        //TODO make sure key size when making btree is correct
                        btf = new BTreeFile("StreamOrderIndex", AttrType.attrString, STR_LEN*3, 2/*delete*/);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        Runtime.getRuntime().exit(1);
                    }
                    while ( temp != null) {
                        try {
                            key = m.getColumnLabel() + " " + m.getRowLabel() + " " + m.getTimeStamp();
                            mid = fscan._mid;
                            System.out.println("stream mid: " + mid.pageNo + " " + mid.slotNo);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            btf.insert(new StringKey(key), mid);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            temp = fscan.get_next();
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case OrderType.type3:
                    //row label then timestamp
                    try {
                        //TODO make sure key size when making btree is correct
                        btf = new BTreeFile("StreamOrderIndex", AttrType.attrString, STR_LEN*2, 3/*delete*/);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        Runtime.getRuntime().exit(1);
                    }
                    while ( temp != null) {
                        try {
                            key = m.getRowLabel() + " " + m.getTimeStamp();
                            mid = fscan._mid;
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            btf.insert(new StringKey(key), mid);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            temp = fscan.get_next();
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case OrderType.type4:
                    //column label then time stamp

                    try {
                        //TODO make sure key size when making btree is correct
                        btf = new BTreeFile("StreamOrderIndex", AttrType.attrString, STR_LEN*2, 4/*delete*/);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        Runtime.getRuntime().exit(1);
                    }
                    while ( temp != null) {
                        try {
                            key = m.getColumnLabel() + " " + m.getTimeStamp();
                            mid = fscan._mid;
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            btf.insert(new StringKey(key), mid);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            temp = fscan.get_next();
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case OrderType.type5:
                    //time stamp
                    int tsKey = 0;
                    try {
                        //TODO make sure key size when making btree is correct
                        btf = new BTreeFile("StreamOrderIndex", AttrType.attrInteger, 4, 5/*delete*/);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        Runtime.getRuntime().exit(1);
                    }
                    while ( temp != null) {
                        try {
                            tsKey = m.getTimeStamp();
                            mid = fscan._mid;
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            btf.insert(new IntegerKey(tsKey), mid);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            temp = fscan.get_next();
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                default:
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // System.out.println("Initializing indexscan, DBname: " + SystemDefs.JavabaseDBName);
        iscan = new IndexScan(new IndexType(IndexType.B_Index), SystemDefs.JavabaseDBName, "StreamOrderIndex", attrType, attrSize, 4, 4, projlist, null, 1, false);


    }


    /**
     * Each filter can either be *, a single value, or a range [x,y]. This function takes the filters and determines
     * What kind of operation each filter is then converts it into the appropriate CondExpr to be used with a FIleScan
     * @param rowFilter
     * @param columnFilter
     * @param valueFilter
     * @return the outFilter to be used with the FileScan
     */
    private CondExpr[] queryFilter(java.lang.String rowFilter, java.lang.String columnFilter, java.lang.String valueFilter) {
        List<CondExpr> query = new ArrayList<CondExpr>();
        // If RowFilter is a range, create a range expression
        if(isRangeQuery(rowFilter)){
            String[] bounds = getBounds(rowFilter);
            for (CondExpr expr : rangeQuery(bounds[0], bounds[1], 0)){
                query.add(expr);
            }
        }
        // Else if rowFilter is an equality, create an identity expression
        else if(isEqualityQuery(rowFilter)){
            query.add(equalityQuery(rowFilter, 0));
        }
        // If ColumnFilter is a range, create a range expression
        if(isRangeQuery(columnFilter)){
            String[] bounds = getBounds(rowFilter);
            for (CondExpr expr : rangeQuery(bounds[0], bounds[1], 1)){
                query.add(expr);
            }
        }
        // Else if columnFilter is an equality, create an identity expression
        else if(isEqualityQuery(columnFilter)){
            query.add(equalityQuery(columnFilter, 1));
        }

        // If ValueFilter is a range, create a range expression
        if(isRangeQuery(valueFilter)){
            String[] bounds = getBounds(rowFilter);
            for (CondExpr expr : rangeQuery(bounds[0], bounds[1], 3)){
                query.add(expr);
            }
        }
        // Else if valueFilter is an equality, create an identity expression
        else if(isEqualityQuery(valueFilter)){
            query.add(equalityQuery(valueFilter, 3));
        }
        // convert results to the format required for scans
        return ConvertToArray(query);
    }

    /**
     * We Determine if a filter is a range by checking for the presence of the ',' character
     * A range query would be in the format [x,y]
     * @param filter
     * @return
     */
    private boolean isRangeQuery(java.lang.String filter){
        return filter.contains(",");
    }

    /**
     * This filter check is context specific, it is always called after isRangeQuery(). In those cases, the only other
     * possible filter under consideration is the single value. A single value will be anything not containing '*'
     * @param filter
     * @return
     */
    private boolean isEqualityQuery(java.lang.String filter){
        return !filter.contains("*");
    }

    /**
     * Parses a range and returns the upper and lower bounds
     * @param filter [x,y] range
     * @return
     */
    private String[] getBounds(java.lang.String filter){
        return filter.replaceAll("(\\[|\\])", "").split(",");
    }

    /**
     * Converts a List of queries into the format required by FileScan, CondExpr[].
     * @param queries A List of Queries
     * @return
     */
    private CondExpr[] ConvertToArray(List<CondExpr> queries){
        // The CondExpr needs to terminate with a null CondExpr,
        // Hence we take the query size + 1
        CondExpr[] plan = new CondExpr[queries.size()+1];
        for (int i = 0; i < queries.size(); i++) {
            plan[i] = queries.get(i);
        }
        plan[queries.size()] = null;
        return plan;
    }

    /**
     * Generates a CondExpr for an equality operation
     * @param rowFilter Single Value Filter
     * @param offSet The OffSet of the map attribute under consideration
     * @return
     */
    private CondExpr equalityQuery(java.lang.String rowFilter, int offSet){
        // set up an equality expression
        CondExpr expr = new CondExpr();
        expr.op = new AttrOperator(AttrOperator.aopEQ);
        expr.type1 = new AttrType(AttrType.attrSymbol);
        expr.type2 = new AttrType(AttrType.attrString);
        expr.operand1.symbol = new FldSpec(new RelSpec(RelSpec.outer), offSet);
        expr.operand2.string = rowFilter;
        expr.next = null;
        return expr;
    }

    /**
     * Generates a set of CondExpr needed for a range search operation
     * @param lowerBound String value of lower bound of range
     * @param upperBound String value of upper bound of range
     * @param offset The OffSet of the map attribute under consideration
     * @return
     */
    private CondExpr[] rangeQuery(java.lang.String lowerBound, java.lang.String upperBound, int offset){
        //range scan
        CondExpr[] expr = new CondExpr[2];
        expr[0] = new CondExpr();
        expr[0].op = new AttrOperator(AttrOperator.aopGE);
        expr[0].type1 = new AttrType(AttrType.attrSymbol);
        expr[0].type2 = new AttrType(AttrType.attrString);
        expr[0].operand1.symbol = new FldSpec(new RelSpec(RelSpec.outer), offset);
        expr[0].operand2.string = lowerBound;
        expr[0].next = null;
        expr[1] = new CondExpr();
        expr[1].op = new AttrOperator(AttrOperator.aopLE);
        expr[1].type1 = new AttrType(AttrType.attrSymbol);
        expr[1].type2 = new AttrType(AttrType.attrString);
        expr[1].operand1.symbol = new FldSpec(new RelSpec(RelSpec.outer), offset);
        expr[1].operand2.string = upperBound;
        expr[1].next = null;
        return expr;
//        CondExpr [] expr2 = new CondExpr[3];
//        expr2[0] = new CondExpr();
//
//
//        expr2[0].next  = null;
//        expr2[0].op    = new AttrOperator(AttrOperator.aopEQ);
//        expr2[0].type1 = new AttrType(AttrType.attrSymbol);
//
//        expr2[0].operand1.symbol = new FldSpec(new RelSpec(RelSpec.outer),1);
//        expr2[0].type2 = new AttrType(AttrType.attrSymbol);
//
//        expr2[0].operand2.symbol = new FldSpec (new RelSpec(RelSpec.innerRel),1);
//
//        expr2[1] = new CondExpr();
//        expr2[1].op   = new AttrOperator(AttrOperator.aopGT);
//        expr2[1].next = null;
//        expr2[1].type1 = new AttrType(AttrType.attrSymbol);
//
//        expr2[1].operand1.symbol = new FldSpec (new RelSpec(RelSpec.outer),4);
//        expr2[1].type2 = new AttrType(AttrType.attrReal);
//        expr2[1].operand2.real = (float)40.0;
//
//
//        expr2[1].next = new CondExpr();
//        expr2[1].next.op   = new AttrOperator(AttrOperator.aopLT);
//        expr2[1].next.next = null;
//        expr2[1].next.type1 = new AttrType(AttrType.attrSymbol); // rating
//        expr2[1].next.operand1.symbol = new FldSpec ( new RelSpec(RelSpec.outer),3);
//        expr2[1].next.type2 = new AttrType(AttrType.attrInteger);
//        expr2[1].next.operand2.integer = 7;
//
//        expr2[2] = null;
    }
    /**
     * Closes the stream object.
     */
    void closestream(){
        if(iscan != null){
            try {
                iscan.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (_bigTable != null) {

            try{
                // unpinPage(datapageId, false);
                // _bigTable.hf.
            }
            catch (Exception e){
                // 	System.err.println("SCAN: Error in Scan" + e);
                e.printStackTrace();
            }
        }
        datapageId.pid = 0;
        _bigTable = null;

        if (dirpage != null) {

            try{
                // unpinPage(dirpageId, false);
            }
            catch (Exception e){
                //     System.err.println("SCAN: Error in Scan: " + e);
                e.printStackTrace();
            }
        }
        dirpage = null;

        nextUserStatus = true;

    }

    /**
     * Retrieve the next map in the stream.
     * 
     * @param mid
     * @return
     */
    public Map getNext() {
        Map recptrmap = null;
        try {
            recptrmap = iscan.get_next();
            if(recptrmap == null)
            {
                System.out.println("scan number: " + nscan);
            }
        } catch (IndexException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        nscan++;
        return recptrmap;

    }
}
