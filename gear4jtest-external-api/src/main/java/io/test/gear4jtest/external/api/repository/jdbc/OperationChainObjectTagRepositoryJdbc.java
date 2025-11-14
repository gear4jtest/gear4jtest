//package io.test.gear4jtest.external.api.repository.jdbc;
//
//import java.sql.SQLException;
//import java.util.ArrayList;
//import java.util.Set;
//import javax.sql.DataSource;
//
//import io.test.gear4jtest.external.api.ExecutionMode;
//import io.test.gear4jtest.external.api.model.OperationChainObject;
//import io.test.gear4jtest.external.api.repository.OperationChainObjectTagRepository;
//
//public final class OperationChainObjectTagRepositoryJdbc implements OperationChainObjectTagRepository {
//    private final DataSource ds;
//
//    public OperationChainObjectTagRepositoryJdbc(DataSource ds) {
//        this.ds = ds;
//    }
//
//    @Override
//    public void addTag(long objectId, String tag) {
//        String sql = "INSERT INTO operation_chain_object_tag(object_id, tag) VALUES (?,?) ON CONFLICT DO NOTHING";
//        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
//            ps.setLong(1, objectId);
//            ps.setString(2, tag);
//            ps.executeUpdate();
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Override
//    public void removeTag(long objectId, String tag) {
//        String sql = "DELETE FROM operation_chain_object_tag WHERE object_id=? AND tag=?";
//        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
//            ps.setLong(1, objectId);
//            ps.setString(2, tag);
//            ps.executeUpdate();
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Override
//    public Set<String> listTags(long objectId) {
//        String sql = "SELECT tag FROM operation_chain_object_tag WHERE object_id=? ORDER BY tag";
//        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
//            ps.setLong(1, objectId);
//            try (var rs = ps.executeQuery()) {
//                Set<String> s = new java.util.LinkedHashSet<>();
//                while (rs.next()) {
//                    s.add(rs.getString(1));
//                }
//                return s;
//            }
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Override
//    public java.util.List<OperationChainObject> findObjectsByTag(String tag, ExecutionMode mode) {
//        String sql = "SELECT o.id, o.al_id, o.version, o.mode, o.content_hash, o.size_bytes, o.mime_type, o.created_at, o.created_by, o.published_at " +
//                "FROM operation_chain_object o JOIN operation_chain_object_tag t ON o.id=t.object_id WHERE t.tag=? " +
//                (mode != null ? " AND o.mode=? " : "") +
//                " ORDER BY o.published_at DESC, o.id DESC";
//        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
//            ps.setString(1, tag);
//            if (mode != null) {
//                ps.setString(2, mode.name());
//            }
//            try (var rs = ps.executeQuery()) {
//                var list = new ArrayList<OperationChainObject>();
//                while (rs.next()) {
//                    list.add(new OperationChainObject(
//                            rs.getLong("id"),
//                            rs.getString("al_id"),
//                            rs.getString("version"),
//                            ExecutionMode.valueOf(rs.getString("mode")),
//                            rs.getString("content_hash"),
//                            rs.getLong("size_bytes"),
//                            rs.getString("mime_type"),
//                            rs.getTimestamp("created_at").toInstant(),
//                            rs.getString("created_by"),
//                            rs.getTimestamp("published_at").toInstant()
//                    ));
//                }
//                return list;
//            }
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
