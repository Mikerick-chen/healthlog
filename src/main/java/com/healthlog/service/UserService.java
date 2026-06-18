package com.healthlog.service;

import com.healthlog.entity.User;
import com.healthlog.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** 使用者業務服務（輕量多使用者：以名稱建立/切換） */
@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    public List<User> findAll() { return repo.findAll(); }

    public User findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("找不到使用者 id=" + id));
    }

    /** 以名稱建立或取得（已存在則直接回傳，達成「輸入名稱即切換」） */
    public User createOrGet(String name, String location) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("使用者名稱不可為空");
        String trimmed = name.trim();
        return repo.findByName(trimmed).orElseGet(() -> {
            User u = new User(trimmed);
            u.setLocation(location);
            return repo.save(u);
        });
    }

    /** 更新使用者定位（供環境關聯分析） */
    public User updateLocation(Long id, String location, Double lat, Double lon) {
        User u = findById(id);
        if (location != null) u.setLocation(location);
        if (lat != null) u.setLatitude(lat);
        if (lon != null) u.setLongitude(lon);
        return repo.save(u);
    }
}
