package com.prosegur.apontamento.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ClarityTask {

    Integer taskId;
    String taskName;
    Integer internalId;

}
