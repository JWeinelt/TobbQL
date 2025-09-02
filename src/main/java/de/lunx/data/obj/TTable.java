package de.lunx.data.obj;

import de.lunx.data.DataManager;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

@Getter
public class TTable {
    private final UUID uniqueID;

    private String name;
    private String dataBase;

    private final List<TColumn> columns = new ArrayList<>();
    private final List<HashMap<String, Object>> data = new ArrayList<>();

    public TTable(String name, String dataBase) {
        this.name = name;
        this.dataBase = dataBase;
        this.uniqueID = UUID.randomUUID();
    }

    public TTable addColumn(TColumn column) {
        columns.add(column);
        return this;
    }

    public boolean insertData(HashMap<String, Object> newRow) throws IllegalArgumentException {
        for (String col : newRow.keySet()) {
            TColumn column = getColumn(col);
            if (column == null) {
                throw new IllegalArgumentException("\"" + col + "\" is not present in target table " + getName());
            }
            if (!column.validate(newRow.get(col))) {
                throw new IllegalArgumentException("Cannot insert data in column " + col + " as of invalid type");
            }
            data.add(newRow);
        }
        return true;
    }

    public void truncate() {
        data.clear();
    }

    public void getThroughRowsWithIndex(BiConsumer<HashMap<String, Object>, Integer> action) {
        for (int i = 0; i < data.size(); i++) {
            action.accept(data.get(i), i);
        }
    }

    public void deleteRow(int row) {
        data.remove(row);
    }

    public TColumn getColumn(String name) {
        for (TColumn c : columns) if (c.getName().equals(name)) return c;
        return null;
    }

    public void build() {
        DataManager.getInstance().getDatabase(dataBase).registerTTable(this);
        DataManager.getInstance().save(this);
    }
}