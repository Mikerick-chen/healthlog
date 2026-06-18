package com.healthlog.service;

import com.healthlog.dto.VitalSignRequest;
import com.healthlog.entity.VitalSign;
import com.healthlog.repository.VitalSignRepository;
import com.healthlog.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 生命徵象業務服務（血壓/血糖/心率/體溫，依使用者隔離） */
@Service
public class VitalSignService {

    private final VitalSignRepository repo;

    public VitalSignService(VitalSignRepository repo) {
        this.repo = repo;
    }

    public List<VitalSign> findAll() { return repo.findByUserIdOrderByRecordDateDesc(CurrentUser.id()); }

    public List<VitalSign> findByRange(LocalDate start, LocalDate end) {
        return repo.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(CurrentUser.id(), start, end);
    }

    public VitalSign create(VitalSignRequest req) {
        VitalSign e = new VitalSign();
        e.setUserId(CurrentUser.id());
        apply(e, req);
        return repo.save(e);
    }

    public VitalSign update(Long id, VitalSignRequest req) {
        VitalSign e = get(id);
        apply(e, req);
        return repo.save(e);
    }

    public void delete(Long id) { repo.delete(get(id)); }

    private VitalSign get(Long id) {
        VitalSign e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到 id=" + id + " 的生命徵象"));
        if (e.getUserId() != null && !e.getUserId().equals(CurrentUser.id()))
            throw new IllegalArgumentException("無權存取此筆資料");
        return e;
    }

    private void apply(VitalSign e, VitalSignRequest req) {
        e.setRecordDate(req.getRecordDate() != null ? req.getRecordDate() : LocalDate.now());
        e.setSystolic(req.getSystolic());
        e.setDiastolic(req.getDiastolic());
        e.setHeartRate(req.getHeartRate());
        e.setBodyTemp(req.getBodyTemp());
        e.setBloodSugar(req.getBloodSugar());
        e.setMeasureContext(req.getMeasureContext());
    }
}
