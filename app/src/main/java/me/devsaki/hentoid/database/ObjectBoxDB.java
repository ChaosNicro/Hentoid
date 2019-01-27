package me.devsaki.hentoid.database;

import android.content.Context;
import android.util.SparseIntArray;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.Query;
import io.objectbox.query.QueryBuilder;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Attribute_;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Content_;
import me.devsaki.hentoid.database.domains.MyObjectBox;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.QueueRecord_;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;

import static com.annimon.stream.Collectors.toList;

public class ObjectBoxDB {

    // TODO - put indexes in the DB

    private static ObjectBoxDB instance;

    private final BoxStore store;
    private final int[] visibleContentStatus = new int[]{StatusContent.DOWNLOADED.getCode(),
            StatusContent.ERROR.getCode(),
            StatusContent.MIGRATED.getCode()};

    private ObjectBoxDB(Context context) {
        store = MyObjectBox.builder().androidContext(context).build();
    }


    // Use this to get db instance
    public static synchronized ObjectBoxDB getInstance(Context context) {
        // Use application context only
        if (instance == null) {
            instance = new ObjectBoxDB(context);
        }

        return instance;
    }

    public void insertContent(Content row) {
        store.boxFor(Content.class).put(row);
    }

    public long countContentEntries() {
        return store.boxFor(Content.class).count();
    }

    public void updateContentStatus(Content row) {
        Box<Content> contentBox = store.boxFor(Content.class);
        Content c = contentBox.get(row.getId());
        c.setStatus(row.getStatus());
        contentBox.put(c);
    }

    public void updateContentStatus(StatusContent updateFrom, StatusContent updateTo) {
        List<Content> content = selectContentByStatus(updateFrom);
        for (int i = 0; i < content.size(); i++) content.get(i).setStatus(updateTo);

        store.boxFor(Content.class).put(content);
    }

    public void updateContentFavourite(Content content) {
        Box<Content> contentBox = store.boxFor(Content.class);
        Content c = contentBox.get(content.getId());
        c.setFavourite(!c.isFavourite());
        contentBox.put(c);
    }

    public List<Content> selectContentByStatus(StatusContent status) {
        return store.boxFor(Content.class).query().equal(Content_.status, status.getCode()).build().find();
    }

    public void deleteContent(Content content) {
        store.boxFor(Content.class).remove(content);
    }

    public void updateContentReads(Content content) {
        Box<Content> contentBox = store.boxFor(Content.class);

        Content c = contentBox.get(content.getId());
        c.setReads(content.getReads());
        c.setLastReadDate(content.getLastReadDate());
        contentBox.put(c);
    }

    public List<QueueRecord> selectQueue() {
        return store.boxFor(QueueRecord.class).query().order(QueueRecord_.rank).build().find();
    }

    public List<Content> selectQueueContents() {
        List<Content> result = Collections.emptyList();
        List<QueueRecord> queueRecords = selectQueue();
        if (queueRecords.size() > 0) {
            long[] contentIds = new long[queueRecords.size()];
            int index = 0;
            for (QueueRecord q : queueRecords) contentIds[index++] = q.content.getTargetId();
            result = selectContentByIds(contentIds);
        }
        return result;
    }

    public void insertQueue(long id, int order) {
        store.boxFor(QueueRecord.class).put(new QueueRecord(id, order));
    }

    public void udpateQueue(long contentId, int newOrder) {
        Box<QueueRecord> queueRecordBox = store.boxFor(QueueRecord.class);

        QueueRecord record = queueRecordBox.query().equal(QueueRecord_.contentId, contentId).order(QueueRecord_.rank).build().findFirst();
        if (record != null) {
            record.rank = newOrder;
            queueRecordBox.put(record);
        }
    }

    public void deleteQueueById(long contentId) {
        store.boxFor(Content.class).remove(contentId);
    }

    long countAllContent() {
        return countContentByQuery("", Collections.emptyList(), false);
    }

    public Content selectContentById(long id) {
        return store.boxFor(Content.class).get(id);
    }

    private List<Content> selectContentByIds(long[] ids) {
        return store.boxFor(Content.class).query().in(Content_.id, ids).build().find();
    }

    public Attribute selectAttributeById(long id) {
        return store.boxFor(Attribute.class).get(id);
    }

    private long[] getIdsFromAttributes(@Nonnull List<Attribute> attrs) {
        long[] result = new long[attrs.size()];
        if (attrs.size() > 0) {
            int index = 0;
            for (Attribute a : attrs) result[index++] = a.getId();
        }
        return result;
    }

    private String[] getNamesFromAttributes(@Nonnull List<Attribute> attrs) {
        String[] result = new String[attrs.size()];
        if (attrs.size() > 0) {
            int index = 0;
            for (Attribute a : attrs) result[index++] = a.getName();
        }
        return result;
    }

    private void applyOrderStyle(QueryBuilder<Content> query, int orderStyle) {
        switch (orderStyle) {
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_FIRST:
                query.orderDesc(Content_.downloadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_DL_DATE_LAST:
                query.order(Content_.downloadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_TITLE_ALPHA:
                query.order(Content_.title);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_TITLE_ALPHA_INVERTED:
                query.orderDesc(Content_.title);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LEAST_READ:
                query.order(Content_.reads).order(Content_.lastReadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_MOST_READ:
                query.orderDesc(Content_.reads).orderDesc(Content_.lastReadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_LAST_READ:
                query.orderDesc(Content_.lastReadDate);
                break;
            case Preferences.Constant.PREF_ORDER_CONTENT_RANDOM:
                // TODO - that one's tricky - see https://github.com/objectbox/objectbox-java/issues/17
//                sql += ContentTable.ORDER_RANDOM.replace("@6", String.valueOf(RandomSeedSingleton.getInstance().getRandomNumber()));
                break;
            default:
                // Nothing
        }
    }

    private Query<Content> buildContentSearchQuery(String title, List<Attribute> metadata, boolean filterFavourites, int orderStyle) {
        AttributeMap metadataMap = new AttributeMap();
        metadataMap.add(metadata);

        boolean hasTitleFilter = (title != null && title.length() > 0);
        boolean hasSiteFilter = metadataMap.containsKey(AttributeType.SOURCE) && (metadataMap.get(AttributeType.SOURCE) != null) && (metadataMap.get(AttributeType.SOURCE).size() > 0);
        boolean hasTagFilter = metadataMap.keySet().size() > (hasSiteFilter ? 1 : 0);

        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (hasSiteFilter)
            query.in(Content_.site, getIdsFromAttributes(metadataMap.get(AttributeType.SOURCE)));
        if (filterFavourites) query.equal(Content_.favourite, true);
        if (hasTitleFilter) query.contains(Content_.title, title);
        if (hasTagFilter) {
            for (AttributeType attrType : metadataMap.keySet()) {
                if (!attrType.equals(AttributeType.SOURCE)) { // Not a "real" attribute in database
                    String[] attrNames = getNamesFromAttributes(metadataMap.get(attrType));
                    if (attrNames.length > 0)
                        query.link(Content_.attributes).equal(Attribute_.type, attrType.getCode()).in(Attribute_.name, attrNames, QueryBuilder.StringOrder.CASE_INSENSITIVE);
                }
            }
        }
        applyOrderStyle(query, orderStyle);

        return query.build();
    }

    // Target Function; does not work with ObjectBox v2.3.1
    private Query<Content> buildUniversalContentSearchQuery(String queryStr, boolean filterFavourites, int orderStyle) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filterFavourites) query.equal(Content_.favourite, true);
        query.contains(Content_.title, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE);
        query.or().link(Content_.attributes).contains(Attribute_.name, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE); // Use of or() here is not possible yet
        applyOrderStyle(query, orderStyle);

        return query.build();
    }

    private Query<Content> buildUniversalContentSearchQueryContent(String queryStr, boolean filterFavourites, long[] additionalIds, int orderStyle) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filterFavourites) query.equal(Content_.favourite, true);
        query.contains(Content_.title, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE);
        query.or().in(Content_.id, additionalIds);
        applyOrderStyle(query, orderStyle);

        return query.build();
    }

    private Query<Content> buildUniversalContentSearchQueryAttributes(String queryStr, boolean filterFavourites) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filterFavourites) query.equal(Content_.favourite, true);
        query.link(Content_.attributes).contains(Attribute_.name, queryStr, QueryBuilder.StringOrder.CASE_INSENSITIVE);

        return query.build();
    }

    private Query<Content> buildContentSearchQueryAttributes(AttributeType type, List<Attribute> attributes) {
        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        String[] attrNames = getNamesFromAttributes(attributes);
        query.link(Content_.attributes).equal(Attribute_.type, type.getCode()).in(Attribute_.name, attrNames, QueryBuilder.StringOrder.CASE_INSENSITIVE);

        return query.build();
    }

    long countContentByQuery(String title, List<Attribute> tags, boolean filterFavourites) {
        Query<Content> query = buildContentSearchQuery(title, tags, filterFavourites, Preferences.Constant.PREF_ORDER_CONTENT_NONE);
        return query.count();
    }

    List<Content> selectContentByQuery(String title, int page, int booksPerPage, List<Attribute> tags, boolean filterFavourites, int orderStyle) {
        int start = (page - 1) * booksPerPage;
        Query<Content> query = buildContentSearchQuery(title, tags, filterFavourites, orderStyle);

        if (booksPerPage < 0) return query.find();
        else return query.find(start, booksPerPage);
    }

    List<Content> selectContentByUniqueQuery(String queryStr, int page, int booksPerPage, boolean filterFavourites, int orderStyle) {
        int start = (page - 1) * booksPerPage;
/*
        Query<Content> query = buildUniversalContentSearchQuery(queryStr, filterFavourites, orderStyle);
*/
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497 and https://github.com/objectbox/objectbox-java/issues/533)
        // querying Content and attributes will have to be done separately
        // TODO optimize by reusing query with parameters
        Query<Content> contentAttrSubQuery = buildUniversalContentSearchQueryAttributes(queryStr, filterFavourites);
        Query<Content> query = buildUniversalContentSearchQueryContent(queryStr, filterFavourites, contentAttrSubQuery.findIds(), orderStyle);

        if (booksPerPage < 0) return query.find();
        else return query.find(start, booksPerPage);
    }

    long countContentByUniqueQuery(String queryStr, boolean filterFavourites) {
/*
        Query<Content> query = buildUniversalContentSearchQuery(queryStr, filterFavourites, orderStyle);
*/
        // Due to objectBox limitations (see https://github.com/objectbox/objectbox-java/issues/497 and https://github.com/objectbox/objectbox-java/issues/533)
        // querying Content and attributes will have to be done separately
        // TODO optimize by reusing query with parameters
        Query<Content> contentAttrSubQuery = buildUniversalContentSearchQueryAttributes(queryStr, filterFavourites);
        Query<Content> query = buildUniversalContentSearchQueryContent(queryStr, filterFavourites, contentAttrSubQuery.findIds(), Preferences.Constant.PREF_ORDER_CONTENT_NONE);
        return query.count();
    }

    private long[] getFilteredContent(List<Attribute> attrs, boolean filterFavourites) {
        if (null == attrs || 0 == attrs.size()) return new long[0];

        QueryBuilder<Content> contentFromSourceQueryBuilder = store.boxFor(Content.class).query();
        contentFromSourceQueryBuilder.in(Content_.status, visibleContentStatus);
        contentFromSourceQueryBuilder.equal(Content_.site, 1);
        if (filterFavourites) contentFromSourceQueryBuilder.equal(Content_.favourite, true);
        Query<Content> contentFromSourceQuery = contentFromSourceQueryBuilder.build(); // TODO - build once and for all ?

        QueryBuilder<Content> contentFromAttributesQueryBuilder = store.boxFor(Content.class).query();
        contentFromAttributesQueryBuilder.in(Content_.status, visibleContentStatus);
        if (filterFavourites) contentFromSourceQueryBuilder.equal(Content_.favourite, true);
        contentFromAttributesQueryBuilder.link(Content_.attributes)
                .equal(Attribute_.type, 0)
                .equal(Attribute_.name, "");
        Query<Content> contentFromAttributesQuery = contentFromAttributesQueryBuilder.build(); // TODO - build once and for all ?

        List<Long> results = Collections.emptyList();
        long[] ids;

        for (Attribute attr : attrs) {
            if (attr.getType().equals(AttributeType.SOURCE)) {
                ids = contentFromSourceQuery.setParameter(Content_.site, attr.getId()).findIds(); // TODO - check if site code is indeed stored in attr.getId ??
            } else {
                ids = contentFromAttributesQuery.setParameter(Attribute_.type, attr.getType().getCode())
                        .setParameter(Attribute_.name, attr.getName()).findIds();
            }
            if (results.isEmpty()) results = Helper.getListFromPrimitiveArray(ids);
            else {
                // Filter results with newly found IDs (only common IDs should stay)
                List<Long> idsAsList = Helper.getListFromPrimitiveArray(ids);
                results = Stream.of(results).filter(idsAsList::contains).collect(toList());
            }
        }

        return Stream.of(results).mapToLong(l -> l).toArray();
    }

    List<Attribute> selectAvailableSources() {
        return selectAvailableSources(null);
    }

    List<Attribute> selectAvailableSources(List<Attribute> filter) {
        List<Attribute> result = new ArrayList<>();

        QueryBuilder<Content> query = store.boxFor(Content.class).query();
        query.in(Content_.status, visibleContentStatus);

        if (filter != null && !filter.isEmpty()) {
            AttributeMap metadataMap = new AttributeMap();
            metadataMap.add(filter);

            List<Attribute> params = metadataMap.get(AttributeType.SOURCE);
            if (params != null && !params.isEmpty())
                query.in(Content_.site, getIdsFromAttributes(params));

            for (AttributeType attrType : metadataMap.keySet()) {
                if (!attrType.equals(AttributeType.SOURCE)) { // Not a "real" attribute in database
                    List<Attribute> attrs = metadataMap.get(attrType);
                    if (attrs.size() > 0) {
                        Query<Content> contentAttrSubQuery = buildContentSearchQueryAttributes(attrType, attrs);
                        query.in(Content_.id, contentAttrSubQuery.findIds());
                    }
                }
            }
        }

        List<Content> content = query.build().find();

        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by source
        Map<Site, List<Content>> map = Stream.of(content).collect(Collectors.groupingBy(Content::getSite));
        for (Site s : map.keySet()) {
            result.add(new Attribute(AttributeType.SOURCE, s.getDescription(), "").setExternalId(s.getCode()).setCount(map.get(s).size()));
        }
        // Order by count desc
        result = Stream.of(result).sortBy(a -> -a.getCount()).collect(toList());

        return result;
    }

    List<Attribute> selectAvailableAttributes(AttributeType type, List<Attribute> attributeFilter, String filter, boolean filterFavourites) {
        // Get Content filtered by current selection
        long[] filteredContent = getFilteredContent(attributeFilter, filterFavourites);
        // Get available attributes of the resulting content list
        QueryBuilder<Attribute> query = store.boxFor(Attribute.class).query();
        query.equal(Attribute_.type, type.getCode());
        if (filter != null && !filter.trim().isEmpty())
            query.contains(Attribute_.name, filter, QueryBuilder.StringOrder.CASE_INSENSITIVE);
        if (filteredContent.length > 0)
            query.link(Attribute_.contents).in(Content_.id, filteredContent);

        List<Attribute> result = query.build().find();

        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by name
        Map<String, List<Attribute>> map = Stream.of(result).collect(Collectors.groupingBy(Attribute::getName));
        for (String s : map.keySet()) {
            result.add(new Attribute(type, s, "").setCount(map.get(s).size())); // URL was irrelevant
        }
        // Order by count desc, name asc
        return Stream.of(result).sortBy(a -> -a.getCount()).sortBy(Attribute::getName).collect(toList());
    }

    SparseIntArray countAvailableAttributesPerType() {
        return countAvailableAttributesPerType(null);
    }

    SparseIntArray countAvailableAttributesPerType(List<Attribute> attributeFilter) {
        // Get Content filtered by current selection
        long[] filteredContent = getFilteredContent(attributeFilter, false);
        // Get available attributes of the resulting content list
        QueryBuilder<Attribute> query = store.boxFor(Attribute.class).query();
        if (filteredContent.length > 0)
            query.link(Attribute_.contents).in(Content_.id, filteredContent);

        List<Attribute> attributes = query.build().find();

        SparseIntArray result = new SparseIntArray();
        // SELECT field, COUNT(*) GROUP BY (field) is not implemented in ObjectBox v2.3.1
        // => Group by and count have to be done manually (thanks God Stream exists !)
        // Group and count by type
        Map<AttributeType, List<Attribute>> map = Stream.of(attributes).collect(Collectors.groupingBy(Attribute::getType));
        for (AttributeType t : map.keySet()) {
            result.append(t.getCode(), map.get(t).size());
        }

        return result;
    }
}
