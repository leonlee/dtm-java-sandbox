package com.dtmcli.java.sample.controller.pressure_measurement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dtmcli.java.sample.param.TransNotifyReq;
import com.dtmcli.java.sample.param.TransReq;

import lombok.extern.slf4j.Slf4j;
import pub.dtm.client.exception.FailureException;
import pub.dtm.client.utils.JsonUtils;

@Slf4j
@RestController
@RequestMapping(("pmeasure/bydb/"))
public class TccPMeasureByDbController {

	private static AtomicInteger userIdIndex = new AtomicInteger(1);

	@Value("${dtm.ipport}")
	private String endpoint;
	@Autowired
	private DataSource dataSource;

	/**
	 * 具有子事务屏障功能的tcc demo (转账成功)
	 *
	 * @return
	 * @throws Exception 
	 */
	@RequestMapping("tccBarrier")
	public String tccBarrier() throws Exception {
		int amount = 30;
		int transOutUserId = userIdIndex.getAndAdd(2);
		transOutUserId = transOutUserId >= 1000 ? transOutUserId % 1000: transOutUserId;
		int transInUserId = transOutUserId + 1;
		LocalDateTime transTime = LocalDateTime.now();
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try {
				// 转出
				TransReq transOutReq = new TransReq(transOutUserId, -amount);
				adjustTrading(connection, transOutReq);
				adjustBalance(connection, transOutReq);
				// 转入
				TransReq transInReq = new TransReq(transInUserId, amount);
				adjustTrading(connection, transInReq);
				adjustBalance(connection, transInReq);
				TransNotifyReq transNotifyReq = 
						new TransNotifyReq(UUID.randomUUID().toString(), transOutUserId, transInUserId, amount, transTime.toString());
				submitNotifyRecord(connection, transNotifyReq);
				adjustNotifyRecord(connection, transNotifyReq, "SEND_CONFIRM");
				connection.commit();
			} catch (Exception exception) {
			    log.warn("barrier call error", exception);
			    connection.rollback();
			    throw exception;
			} finally {
			    connection.setAutoCommit(true);
			}
		}
		return "success";
	}
	
	private void submitNotifyRecord(Connection connection, TransNotifyReq transReq) throws SQLException, FailureException {
        String sql = """
        		INSERT INTO `dtm_busi`.`user_trans_notify` (`trans_id`, `trans_out_user_id`, `trans_in_user_id`, `trans_amount`, `notify_status`, `trans_time`) 
        			VALUES (?, ?, ?, ?, ?, ?);
        		""";
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, transReq.transId());
            preparedStatement.setInt(2, transReq.transOutUserId());
            preparedStatement.setInt(3, transReq.transInUserId());
            preparedStatement.setInt(4, transReq.amount());
            preparedStatement.setString(5, "SEND_PENDING");
            preparedStatement.setString(6, transReq.transTime().toString());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("转账通知记录创建完成");
            } else {
                throw new FailureException("创建失败");
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }
    }
    
    private void adjustNotifyRecord(Connection connection, TransNotifyReq transReq, String status) throws SQLException {
        PreparedStatement preparedStatement = null;
        try {
            String sql = "update dtm_busi.user_trans_notify set notify_status = ? where trans_id = ?";
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, status);
            preparedStatement.setString(2, transReq.transId());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("转账通知状态更新完成");
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }
    }
    
    /**
     * 更新交易金额
     *
     * @param connection
     * @param transReq
     * @throws SQLException
     */
    public void adjustTrading(Connection connection, TransReq transReq) throws Exception {
        String sql = "update dtm_busi.user_account set trading_balance=trading_balance+?"
                + " where user_id=? and trading_balance + ? + balance >= 0";
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, transReq.getAmount());
            preparedStatement.setInt(2, transReq.getUserId());
            preparedStatement.setInt(3, transReq.getAmount());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("交易金额更新成功");
            } else {
                throw new FailureException("交易失败:" + JsonUtils.toJson(transReq));
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }
        
    }
    
    /**
     * 更新余额
     */
    public void adjustBalance(Connection connection, TransReq transReq) throws SQLException {
        PreparedStatement preparedStatement = null;
        try {
            String sql = "update dtm_busi.user_account set trading_balance=trading_balance-?,balance=balance+? where user_id=?";
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setInt(1, transReq.getAmount());
            preparedStatement.setInt(2, transReq.getAmount());
            preparedStatement.setInt(3, transReq.getUserId());
            if (preparedStatement.executeUpdate() > 0) {
                System.out.println("余额更新成功");
            }
        } finally {
            if (null != preparedStatement) {
                preparedStatement.close();
            }
        }
    }

}