package com.jobsearch.joblist.run;

import com.jobsearch.joblist.source.SourceName;
import java.math.BigDecimal;

public record SourceSpend(SourceName source, BigDecimal costUsd) {
}
