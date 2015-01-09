package com.aerofs.baseline.sample.db;

import com.aerofs.baseline.sample.api.Customer;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Customer DAO.
 */
@RegisterMapper(Customers.CustomerMapper.class)
public interface Customers {

    @GetGeneratedKeys
    @SqlUpdate("insert into customers(customer_name, organization_name, seats) values(:customer_name, :organization_name, :seats)")
    public int add(@Bind("customer_name") String customerName, @Bind("organization_name") String organizationName, @Bind("seats") int seats);

    @SqlUpdate("update customers set seats = :seats where customer_id = :customer_id")
    public void update(@Bind("customer_id") int customerId, @Bind("seats") int seats);

    @SqlQuery("select count(*) from customers where customer_id = :customer_id")
    public int exists(@Bind("customer_id") int customerId);

    @Nullable
    @SqlQuery("select customer_id, customer_name, organization_name, seats from customers where customer_id = :customer_id")
    public Customer get(@Bind("customer_id") int id);

    @SqlQuery("select customer_id, customer_name, organization_name, seats from customers")
    public ResultIterator<Customer> get();

    public static final class CustomerMapper implements ResultSetMapper<Customer> {

        @Override
        public Customer map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new Customer(r.getInt(1), r.getString(2), r.getString(3), r.getInt(4));
        }
    }
}
