package com.jobsearch.core_api.auth.account;

import com.jobsearch.core_api.config.AppProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the plain-text mails of the account flows. The send happens on its own thread, so a slow SMTP server neither holds
 * the request nor lets its timing tell whether an address has an account. Without {@code spring.mail.host} nothing is
 * sent and the mail is only logged: fine on a laptop, and refused at startup when strict secrets are on.
 */
@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final JavaMailSender sender;
	private final String from;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public MailService(ObjectProvider<JavaMailSender> sender, AppProperties appProperties) {
		this.sender = sender.getIfAvailable();
		this.from = appProperties.getMail().getFrom();
	}

	public void send(String to, String subject, String text) {
		if (sender == null) {
			log.info("Mail is not configured, so this was not sent. to={} subject={}\n{}", to, subject, text);
			return;
		}
		executor.execute(() -> {
			try {
				SimpleMailMessage message = new SimpleMailMessage();
				message.setFrom(from);
				message.setTo(to);
				message.setSubject(subject);
				message.setText(text);
				sender.send(message);
			}
			catch (RuntimeException ex) {
				// The address and the link stay out of the log: the user can ask for another mail.
				log.error("Sending a mail failed subject={} error={}", subject, ex.getClass().getSimpleName());
			}
		});
	}

	@PreDestroy
	void close() {
		executor.close();
	}
}
