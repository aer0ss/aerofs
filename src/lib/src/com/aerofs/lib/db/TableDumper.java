package com.aerofs.lib.db;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

import com.aerofs.base.BaseUtil;
import com.google.common.collect.Lists;

public class TableDumper
{
    static enum Align {
        LEFT,
        RIGHT,
    }

    PrintWriter _pw;

    public TableDumper(PrintWriter pw)
    {
        _pw = pw;
    }

    static interface CellFormatter
    {
        String format(ResultSet rs, int col) throws SQLException;

        static class StringFormatter implements CellFormatter
        {
            @Override
            public String format(ResultSet rs, int col) throws SQLException
            {
                return String.valueOf(rs.getString(col));
            }
        }

        static class HexFormatter implements CellFormatter
        {
            @Override
            public String format(ResultSet rs, int col) throws SQLException
            {
                return BaseUtil.hexEncode(rs.getBytes(col));
            }
        }

        static StringFormatter STRING = new StringFormatter();
        static HexFormatter HEX = new HexFormatter();

    }

    public void dumpTable(Statement s, String table) throws SQLException, IOException
    {
        dump(s, "SELECT * FROM " + table);
    }

    public void dump(Statement s, String sql) throws SQLException, IOException
    {
        _pw.println(sql);
        printRepeated('=', sql.length());
        _pw.println();
        ResultSet rs = s.executeQuery(sql);
        try {
            dump(rs);
        } finally {
            rs.close();
        }
    }

    protected CellFormatter getFormatter(ResultSetMetaData metadata, int col) throws SQLException
    {
        int type = metadata.getColumnType(col);
        switch (type) {
        case Types.BINARY:
        case Types.BLOB:
        case Types.VARBINARY:
            return CellFormatter.HEX;
        default:
            return CellFormatter.STRING;
        }
    }

    protected Align getAlignment(ResultSetMetaData metadata, int col) throws SQLException
    {
        int type = metadata.getColumnType(col);
        switch (type) {
        case Types.BIGINT:
        case Types.DECIMAL:
        case Types.DOUBLE:
        case Types.FLOAT:
        case Types.INTEGER:
        case Types.NUMERIC:
        case Types.REAL:
        case Types.ROWID:
        case Types.SMALLINT:
        case Types.TINYINT:
            return Align.RIGHT;
        default:
            return Align.LEFT;
        }
    }

    public void dump(ResultSet rs) throws SQLException, IOException
    {
        ResultSetMetaData metadata = rs.getMetaData();
        int numColumns = metadata.getColumnCount();
        int[] widths = new int[numColumns];
        Align[] align = new Align[numColumns];

        CellFormatter[] formatters = new CellFormatter[numColumns];

        String[] headings = new String[numColumns];
        for (int i = 0; i < numColumns; ++i) {
            headings[i] = metadata.getColumnLabel(i + 1);
            widths[i] = headings[i].length();
            formatters[i] = getFormatter(metadata, i + 1);
            align[i] = getAlignment(metadata, i + 1);
        }

        List<String[]> rows = Lists.newArrayList();
        while (rs.next()) {
            String[] row = new String[numColumns];
            for (int i = 0; i < numColumns; ++i) {
                row[i] = formatters[i].format(rs, i + 1);
                widths[i] = Math.max(widths[i], row[i].length());
            }
            rows.add(row);
        }

        for (int i = 0; i < numColumns; ++i) {
            printPadded(headings[i], widths[i], align[i]);
            _pw.append(' ');
        }
        _pw.println();
        for (int i = 0; i < numColumns; ++i) {
            String dashes = repeated('-', headings[i].length());
            printPadded(dashes, widths[i], align[i]);
            _pw.append(' ');
        }
        _pw.println();
        for (String[] row : rows) {
            for (int i = 0; i < numColumns; ++i) {
                printPadded(row[i], widths[i], align[i]);
                _pw.append(' ');
            }
            _pw.println();
        }
        _pw.println();
    }

    String repeated(char ch, int num) {
        char[] chars = new char[num];
        Arrays.fill(chars, ch);
        return new String(chars);
    }

    void printRepeated(char c, int n) throws IOException {
        for (int i = 0; i < n; ++i) _pw.append(c);
    }

    void printPadding(int width) throws IOException {
        printRepeated(' ', width);
    }

    void printPadded(String s, int width, Align align) throws IOException
    {
        int padding = width - s.length();
        if (align == Align.RIGHT && padding > 0) printPadding(padding);
        _pw.print(s);
        if (align == Align.LEFT && padding > 0) printPadding(padding);
    }
}
