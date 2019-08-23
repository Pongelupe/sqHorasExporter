package com.prosegur.apontamento.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ClarityTimesheet {

    private Integer id;
    private LocalDate startDate;
    private LocalDate finishDate;
    private List<ClarityTask> tasks;

}
