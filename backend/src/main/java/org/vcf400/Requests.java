package org.vcf400;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class Requests {
    private Requests() {}
    public record VoteInput(@NotNull @Min(0) @Max(9999) Integer badge,
                            @NotNull @Size(max = 9) String exhibit,
                            @NotNull @Min(0) @Max(999) Integer award) {}
    public record CommentInput(@NotNull @Size(max = 16) String name,
                               @NotNull @Size(max = 9) String exhibit,
                               @NotNull @Size(max = 200) String comment) {}
    public record BranchInput(@Min(0) @Max(9999) Integer from,
                              @Pattern(regexp = "F3|F5|F8") @NotNull String action,
                              @Min(0) @Max(9999) Integer backPage) {}
    public record AuthInput(@NotNull @Size(max = 9) String password) {}
    public record SettingsInput(@Pattern(regexp = "Y|N") String ALWVOTE,
                                @Size(min = 1, max = 9) String ADMPSWRD) {}
}
