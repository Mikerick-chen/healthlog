package com.healthlog.controller;

import com.healthlog.entity.User;
import com.healthlog.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 使用者 REST 控制器（多使用者地基，§9） */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /** GET /users — 列出所有使用者（供前端切換清單） */
    @GetMapping
    public List<User> list() { return service.findAll(); }

    /** GET /users/{id} — 取得單一使用者 */
    @GetMapping("/{id}")
    public User get(@PathVariable Long id) { return service.findById(id); }

    /** POST /users — 以名稱建立或登入（已存在則直接回傳） */
    @PostMapping
    public ResponseEntity<User> createOrGet(@RequestBody Map<String, String> body) {
        User u = service.createOrGet(body.get("name"), body.get("location"));
        return ResponseEntity.status(HttpStatus.CREATED).body(u);
    }

    /** PUT /users/{id}/location — 更新定位（供環境關聯分析） */
    @PutMapping("/{id}/location")
    public User updateLocation(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String location = body.get("location") == null ? null : body.get("location").toString();
        Double lat = body.get("latitude") == null ? null : Double.valueOf(body.get("latitude").toString());
        Double lon = body.get("longitude") == null ? null : Double.valueOf(body.get("longitude").toString());
        return service.updateLocation(id, location, lat, lon);
    }
}
