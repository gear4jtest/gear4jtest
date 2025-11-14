package io.test.gear4jtest.external.api.repository.jdbc;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

import io.test.gear4jtest.external.api.repository.OperationChainTagRepository;

public final class OperationChainTagRepositoryJdbc implements OperationChainTagRepository {
    private final DataSource ds;

    public OperationChainTagRepositoryJdbc(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public void addTag(String alId, String tag) {
        String sql = "INSERT INTO operation_chain_tag(al_id, tag) VALUES (?,?) ON CONFLICT DO NOTHING";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            ps.setString(2, tag);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeTag(String alId, String tag) {
        String sql = "DELETE FROM operation_chain_tag WHERE al_id=? AND tag=?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            ps.setString(2, tag);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> listTags(String alId) {
        String sql = "SELECT tag FROM operation_chain_tag WHERE al_id=? ORDER BY tag";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            try (var rs = ps.executeQuery()) {
                Set<String> s = new java.util.LinkedHashSet<>();
                while (rs.next()) s.add(rs.getString(1));
                return s;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> findAssemblyLineIdsByTag(String tag) {
        String sql = "SELECT al_id FROM operation_chain_tag WHERE tag=? ORDER BY al_id";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, tag);
            try (var rs = ps.executeQuery()) {
                var list = new java.util.ArrayList<String>();
                while (rs.next()) list.add(rs.getString(1));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
