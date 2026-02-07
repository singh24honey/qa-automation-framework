package com.company.qa.playwright.pages;

import com.microsoft.playwright.Page;

/**
 * Sample Dashboard Page Object for testing registry scanner.
 */
public class DashboardPage {

    private final Page page;
    public static final String URL = "/dashboard";

    public DashboardPage(Page page) {
        this.page = page;
    }

    public boolean isDisplayed() {
        return page.url().contains("/dashboard");
    }

    public String getWelcomeMessage() {
        return page.getByTestId("welcome-msg").textContent();
    }

    public void logout() {
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Logout")).click();
    }
}