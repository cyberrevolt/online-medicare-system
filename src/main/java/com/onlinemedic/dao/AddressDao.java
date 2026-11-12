package com.onlinemedic.dao;

import com.onlinemedic.util.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AddressDao {
    public List<Map<String, Object>> listForUser(long userId) throws SQLException {
        String sql = "SELECT id,label,line1,city,district,state,postal_code,is_default FROM addresses WHERE user_id=? ORDER BY is_default DESC,id DESC";
        try (Connection connection = Database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                List<Map<String, Object>> addresses = new ArrayList<>();
                while (result.next()) {
                    Map<String, Object> address = new LinkedHashMap<>();
                    address.put("id", result.getLong("id")); address.put("label", result.getString("label"));
                    address.put("line1", result.getString("line1")); address.put("city", result.getString("city"));
                    address.put("district", result.getString("district")); address.put("state", result.getString("state"));
                    address.put("postalCode", result.getString("postal_code")); address.put("isDefault", result.getBoolean("is_default"));
                    addresses.add(address);
                }
                return addresses;
            }
        }
    }
}
