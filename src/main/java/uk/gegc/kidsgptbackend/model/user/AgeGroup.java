package uk.gegc.kidsgptbackend.model.user;

public enum AgeGroup {
    AGE_6_8(6, 8),
    AGE_9_10(9, 10),
    AGE_11_12(11, 12),
    AGE_13_14(13, 14),
    AGE_15_16(15, 16);

    private final int minAge;
    private final int maxAge;

    AgeGroup(int minAge, int maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public int getMinAge() {
        return minAge;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public static AgeGroup fromAge(int age) {
        for (AgeGroup group : values()) {
            if (age >= group.minAge && age <= group.maxAge) {
                return group;
            }
        }
        throw new IllegalArgumentException("No AgeGroup for age: " + age);
    }
} 