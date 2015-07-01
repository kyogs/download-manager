package com.novoda.downloadmanager.lib;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * This class may be used to filter download manager queries.
 */
public class Query {
    /**
     * Constant for use with {@link #orderBy}
     */
    static final int ORDER_ASCENDING = 1;

    /**
     * Constant for use with {@link #orderBy}
     */
    static final int ORDER_DESCENDING = 2;

    private long[] downloadIds = null;
    private long[] batchIds = null;
    private Integer statusFlags = null;
    private String orderByColumn = Downloads.Impl.COLUMN_LAST_MODIFICATION;
    private int orderDirection = ORDER_DESCENDING;
    private boolean onlyIncludeVisibleInDownloadsUi = false;
    private String[] filterExtras;

    /**
     * Include only the downloads with the given IDs.
     *
     * @return this object
     */
    public Query setFilterById(long... downloadIds) {
        this.downloadIds = downloadIds;
        return this;
    }

    /**
     * Include only the downloads with the given batch IDs.
     *
     * @return this object
     */
    public Query setFilterByBatchId(long... batchIds) {
        this.batchIds = batchIds;
        return this;
    }

    /**
     * Include only the downloads with the given extras.
     *
     * @return this object
     */
    public Query setFilterByExtras(String... extras) {
        filterExtras = extras;
        return this;
    }

    /**
     * Include only downloads with status matching any the given status flags.
     *
     * @param flags any combination of the STATUS_* bit flags
     * @return this object
     */
    public Query setFilterByStatus(int flags) {
        statusFlags = flags;
        return this;
    }

    /**
     * Controls whether this query includes downloads not visible in the system's Downloads UI.
     *
     * @param value if true, this query will only include downloads that should be displayed in
     *              the system's Downloads UI; if false (the default), this query will include
     *              both visible and invisible downloads.
     * @return this object
     */
    public Query setOnlyIncludeVisibleInDownloadsUi(boolean value) {
        onlyIncludeVisibleInDownloadsUi = value;
        return this;
    }

    /**
     * Change the sort order of the returned Cursor.
     *
     * @param column    one of the COLUMN_* constants; currently, only
     *                  {DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP} and {DownloadManager.COLUMN_TOTAL_SIZE_BYTES} are
     *                  supported.
     * @param direction either {@link #ORDER_ASCENDING} or {@link #ORDER_DESCENDING}
     * @return this object
     */
    Query orderBy(String column, int direction) {
        if (direction != ORDER_ASCENDING && direction != ORDER_DESCENDING) {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }

        switch (column) {
            case DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP:
                orderByColumn = Downloads.Impl.COLUMN_LAST_MODIFICATION;
                break;
            case DownloadManager.COLUMN_TOTAL_SIZE_BYTES:
                orderByColumn = Downloads.Impl.COLUMN_TOTAL_BYTES;
                break;
            default:
                throw new IllegalArgumentException("Cannot order by " + column);
        }
        orderDirection = direction;
        return this;
    }

    /**
     * Run this query using the given ContentResolver.
     *
     * @param projection the projection to pass to ContentResolver.query()
     * @return the Cursor returned by ContentResolver.query()
     */
    Cursor runQuery(ContentResolver resolver, String[] projection, Uri baseUri) {
        List<String> selectionParts = new ArrayList<>();
        String[] selectionArgs = getIdsAsStringArray(downloadIds);

        filterByDownloadIds(selectionParts);
        filterByBatchIds(selectionParts);
        filterByExtras(selectionParts);
        filterByStatus(selectionParts);

        if (onlyIncludeVisibleInDownloadsUi) {
            selectionParts.add(Downloads.Impl.COLUMN_IS_VISIBLE_IN_DOWNLOADS_UI + " != '0'");
        }

        // only return rows which are not marked 'deleted = 1'
        selectionParts.add(Downloads.Impl.COLUMN_DELETED + " != '1'");

        String selection = joinStrings(" AND ", selectionParts);
        String orderDirection = (this.orderDirection == ORDER_ASCENDING ? "ASC" : "DESC");
        String orderBy = orderByColumn + " " + orderDirection;

        return resolver.query(baseUri, projection, selection, selectionArgs, orderBy);
    }

    private String[] getIdsAsStringArray(long[] ids) {
        if (ids == null) {
            return null;
        }
        return DownloadManager.longArrayToStringArray(ids);
    }

    private void filterByDownloadIds(List<String> selectionParts) {
        if (downloadIds == null) {
            return;
        }
        selectionParts.add(DownloadManager.getWhereClauseForIds(downloadIds));
    }

    private void filterByBatchIds(List<String> selectionParts) {
        if (batchIds == null) {
            return;
        }
        selectionParts.add(getWhereClauseForBatchIds(batchIds));
    }

    /**
     * Get a SQL WHERE clause to select a bunch of IDs.
     */
    private String getWhereClauseForBatchIds(long[] ids) {
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("(");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                whereClause.append("OR ");
            }
            whereClause.append(Downloads.Impl.COLUMN_BATCH_ID)
                    .append(" = ")
                    .append(ids[i])
                    .append(" ");
        }
        whereClause.append(")");
        return whereClause.toString();
    }

    private void filterByExtras(List<String> selectionParts) {
        if (filterExtras == null) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String filterExtra : filterExtras) {
            parts.add(extrasClause(filterExtra));
        }
        selectionParts.add(joinStrings(" OR ", parts));
    }

    private void filterByStatus(List<String> selectionParts) {
        if (statusFlags == null) {
            return;
        }

        List<String> parts = new ArrayList<>();
        if ((statusFlags & DownloadManager.STATUS_PENDING) != 0) {
            parts.add(statusClause("=", Downloads.Impl.STATUS_PENDING));
        }
        if ((statusFlags & DownloadManager.STATUS_RUNNING) != 0) {
            parts.add(statusClause("=", Downloads.Impl.STATUS_RUNNING));
        }
        if ((statusFlags & DownloadManager.STATUS_PAUSED) != 0) {
            parts.add(statusClause("=", Downloads.Impl.STATUS_PAUSED_BY_APP));
            parts.add(statusClause("=", Downloads.Impl.STATUS_WAITING_TO_RETRY));
            parts.add(statusClause("=", Downloads.Impl.STATUS_WAITING_FOR_NETWORK));
            parts.add(statusClause("=", Downloads.Impl.STATUS_QUEUED_FOR_WIFI));
        }
        if ((statusFlags & DownloadManager.STATUS_SUCCESSFUL) != 0) {
            parts.add(statusClause("=", Downloads.Impl.STATUS_SUCCESS));
        }
        if ((statusFlags & DownloadManager.STATUS_FAILED) != 0) {
            parts.add("(" + statusClause(">=", 400) + " AND " + statusClause("<", 600) + ")");
        }
        selectionParts.add(joinStrings(" OR ", parts));
    }

    private String joinStrings(String joiner, Iterable<String> parts) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String part : parts) {
            if (!first) {
                builder.append(joiner);
            }
            builder.append(part);
            first = false;
        }
        return builder.toString();
    }

    private String extrasClause(String extra) {
        return Downloads.Impl.COLUMN_NOTIFICATION_EXTRAS + " = '" + extra + "'";
    }

    private String statusClause(String operator, int value) {
        return Downloads.Impl.COLUMN_STATUS + operator + "'" + value + "'";
    }
}
