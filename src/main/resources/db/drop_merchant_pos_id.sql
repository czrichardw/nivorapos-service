alter table if exists merchant
    drop column if exists merchant_pos_id;

alter table if exists user_detail
    drop column if exists merchant_pos_id;
