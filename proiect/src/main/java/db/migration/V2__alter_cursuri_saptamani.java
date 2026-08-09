package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V2__alter_cursuri_saptamani extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        DatabaseMetaData metaData = context.getConnection().getMetaData();

        alterColumnIfRequired(context, metaData, "cursuri", "data_inceput");
        alterColumnIfRequired(context, metaData, "cursuri", "data_sfarsit");

        if (!hasUniqueIndex(metaData, "saptamani", "uk_saptamani_curs_nr")) {
            executeIgnoringExistingConstraint(
                    context,
                    "ALTER TABLE saptamani ADD CONSTRAINT uk_saptamani_curs_nr UNIQUE (id_curs, nr_saptamana)",
                    "uk_saptamani_curs_nr"
            );
        }
    }

    private void alterColumnIfRequired(Context context, DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        if (isColumnNullable(metaData, tableName, columnName)) {
            return;
        }

        execute(context, "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " DROP NOT NULL");
    }

    private boolean isColumnNullable(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        for (String tableCandidate : candidatesFor(tableName)) {
            for (String columnCandidate : candidatesFor(columnName)) {
                try (ResultSet columns = metaData.getColumns(null, null, tableCandidate, columnCandidate)) {
                    if (columns.next()) {
                        return columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                    }
                }
            }
        }

        return false;
    }

    private boolean hasUniqueIndex(DatabaseMetaData metaData, String tableName, String indexName) throws SQLException {
        for (String tableCandidate : candidatesFor(tableName)) {
            try (ResultSet indexes = metaData.getIndexInfo(null, null, tableCandidate, true, false)) {
                while (indexes.next()) {
                    String currentIndexName = indexes.getString("INDEX_NAME");
                    if (currentIndexName != null && currentIndexName.equalsIgnoreCase(indexName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void execute(Context context, String sql) throws SQLException {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute(sql);
        }
    }

    private void executeIgnoringExistingConstraint(Context context, String sql, String constraintName) throws SQLException {
        try {
            execute(context, sql);
        } catch (SQLException exception) {
            String message = exception.getMessage();
            if (message == null) {
                throw exception;
            }

            String normalizedMessage = message.toLowerCase(Locale.ROOT);
            String normalizedConstraintName = constraintName.toLowerCase(Locale.ROOT);
            if (normalizedMessage.contains(normalizedConstraintName)
                    && (normalizedMessage.contains("already")
                    || normalizedMessage.contains("exists")
                    || normalizedMessage.contains("duplicate"))) {
                return;
            }

            throw exception;
        }
    }

    private String[] candidatesFor(String value) {
        return new String[] { value, value.toLowerCase(Locale.ROOT), value.toUpperCase(Locale.ROOT) };
    }
}
