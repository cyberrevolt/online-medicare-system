package com.onlinemedic.dao;

import com.onlinemedic.model.PharmacyProfile;
import com.onlinemedic.util.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class PharmacyDao {
    public PharmacyProfile findForManager(long userId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT pharmacy_name,profile_image FROM pharmacy_settings WHERE id=1")) {
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) return new PharmacyProfile(r.getString(1), r.getString(2));
            }
        }
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT pharmacy_name,profile_image FROM pharmacist_profiles WHERE user_id=?")) {
            p.setLong(1, userId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) return new PharmacyProfile(r.getString(1), r.getString(2));
            }
        }
        return new PharmacyProfile("", null);
    }

    public void updateStorefront(long actorId, String name, String imageUrl) throws SQLException {
        if (name == null || name.trim().length() < 2 || name.trim().length() > 180)
            throw new IllegalArgumentException("Enter a pharmacy name between 2 and 180 characters.");
        name = name.trim();
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String savedImage = null;
                boolean exists;
                try (PreparedStatement p = c.prepareStatement("SELECT profile_image FROM pharmacy_settings WHERE id=1 FOR UPDATE");
                     ResultSet r = p.executeQuery()) {
                    exists = r.next();
                    if (exists) savedImage = r.getString(1);
                }
                if (!exists) {
                    try (PreparedStatement p = c.prepareStatement("SELECT profile_image FROM pharmacist_profiles WHERE user_id=?")) {
                        p.setLong(1, actorId);
                        try (ResultSet r = p.executeQuery()) { if (r.next()) savedImage = r.getString(1); }
                    }
                }
                if (imageUrl == null) imageUrl = savedImage;

                if (exists) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE pharmacy_settings SET pharmacy_name=?,profile_image=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=1")) {
                        p.setString(1, name); p.setString(2, imageUrl); p.setLong(3, actorId); p.executeUpdate();
                    }
                } else {
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO pharmacy_settings(id,pharmacy_name,profile_image,updated_by) VALUES(1,?,?,?)")) {
                        p.setString(1, name); p.setString(2, imageUrl); p.setLong(3, actorId); p.executeUpdate();
                    }
                }

                int updated;
                try (PreparedStatement p = c.prepareStatement("UPDATE pharmacist_profiles SET pharmacy_name=?,profile_image=? WHERE user_id=?")) {
                    p.setString(1, name); p.setString(2, imageUrl); p.setLong(3, actorId); updated = p.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement p = c.prepareStatement("INSERT INTO pharmacist_profiles(user_id,pharmacy_name,profile_image) VALUES(?,?,?)")) {
                        p.setLong(1, actorId); p.setString(2, name); p.setString(3, imageUrl); p.executeUpdate();
                    }
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'PHARMACY UPDATED',?)")) {
                    p.setLong(1, actorId); p.setString(2, "Storefront profile updated: " + name); p.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
