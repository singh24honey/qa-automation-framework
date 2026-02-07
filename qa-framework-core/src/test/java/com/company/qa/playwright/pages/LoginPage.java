package com.company.qa.playwright.pages;

import com.microsoft.playwright.Page;

/**
 * Sample Login Page Object for testing registry scanner.
 */
public class LoginPage {

    private final Page page;
    public static final String URL = "/login";

    public LoginPage(Page page) {
        this.page = page;
    }

    public void navigate() {
        page.navigate(URL);
    }

    public void login(String email, String password) {
        page.getByLabel("Email").fill(email);
        page.getByLabel("Password").fill(password);
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Sign In")).click();
    }

    public String getErrorMessage() {
        return page.getByTestId("login-error").textContent();
    }

    public boolean isLoginSuccessful() {
        return page.url().contains("/dashboard");
    }
}