package com.aerofs.sp.common;

public enum SubscriptionCategory
{
    /*
     * although the ordering shouldn't matter, the constructor parameter does.
     * The constructor parameter for each enum value is used as the categoryId in the database
     * and should not be changed once the code has been deployed
     * (e.g. AEROFS_INVITATION_REMINDER should always have a code 0)
     *
     * the reason we don't simply use the ordinal value of each category ID is to prevent against
     * easy mistakes such as re-ordering of enums
     */

    AEROFS_INVITATION_REMINDER(0),
    NEWSLETTER(1);

    private final int _categoryId;

    SubscriptionCategory(final int categoryId)
    {
        _categoryId = categoryId;
    }

    public int getCategoryID() { return _categoryId; }

    public static SubscriptionCategory getCategoryByID(int value)
    {
        for (SubscriptionCategory sc : SubscriptionCategory.values()) {
            if (value == sc.getCategoryID()) return sc;
        }
        return null;
    }

}
