/**
 * BloodDonation.lk Client Interactions & Component Behaviors
 */

// Function to display popup notification (matching blooddonation.lk)
function showPopup(message) {
    // Remove existing popup if any
    const existing = document.querySelector('.popup');
    if (existing) existing.remove();

    const popup = document.createElement("div");
    popup.className = "popup";
    popup.innerHTML = `
        <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="#216aca" style="margin-right:10px;flex-shrink:0;">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
        </svg>
        <span>${message}</span>
    `;
    document.body.appendChild(popup);

    // Auto-remove after 3.5 seconds
    setTimeout(function() {
        popup.style.opacity = '0';
        popup.style.transition = 'opacity 0.4s ease';
        setTimeout(() => popup.remove(), 400);
    }, 3500);
}

// Phone number auto-formatter
function formatPhoneNumber(input) {
    let value = input.value.replace(/\D/g, '');
    if (value.length > 10) value = value.substring(0, 10);
    input.value = value;
}

// Copy phone number to clipboard with feedback
function copyToClipboard(text, label = "Phone number") {
    if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(() => {
            showPopup(`${label} copied to clipboard!`);
        }).catch(() => {
            prompt("Copy to clipboard: Ctrl+C, Enter", text);
        });
    } else {
        prompt("Copy to clipboard: Ctrl+C, Enter", text);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    // 1. Sticky Navbar on Scroll
    const headerBottom = document.querySelector('.header-bottom');
    const backTopBtn = document.querySelector('.back-top-btn');

    window.addEventListener('scroll', () => {
        const scrollY = window.scrollY;

        if (headerBottom) {
            if (scrollY > 120) {
                headerBottom.classList.add('is-sticky');
            } else {
                headerBottom.classList.remove('is-sticky');
            }
        }

        if (backTopBtn) {
            if (scrollY > 300) {
                backTopBtn.classList.add('is-active');
            } else {
                backTopBtn.classList.remove('is-active');
            }
        }
    });

    if (backTopBtn) {
        backTopBtn.addEventListener('click', (e) => {
            e.preventDefault();
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    }

    // 2. Mobile Navigation Drawer
    const navToggleBtn = document.querySelector('[data-nav-toggler]');
    const mobileDrawer = document.getElementById('mobile-drawer');
    const mobileBackdrop = document.getElementById('mobile-backdrop');
    const mobileCloseBtn = document.getElementById('mobile-drawer-close');

    function openMobileMenu() {
        if (mobileDrawer) mobileDrawer.classList.add('is-active');
        if (mobileBackdrop) mobileBackdrop.classList.add('is-active');
        document.body.style.overflow = 'hidden';
    }

    function closeMobileMenu() {
        if (mobileDrawer) mobileDrawer.classList.remove('is-active');
        if (mobileBackdrop) mobileBackdrop.classList.remove('is-active');
        document.body.style.overflow = '';
    }

    if (navToggleBtn) {
        navToggleBtn.addEventListener('click', openMobileMenu);
    }
    if (mobileCloseBtn) {
        mobileCloseBtn.addEventListener('click', closeMobileMenu);
    }
    if (mobileBackdrop) {
        mobileBackdrop.addEventListener('click', closeMobileMenu);
    }

    // 3. Dynamic Availability Slots Builder (Register & Edit pages)
    const addSlotBtn = document.getElementById('add-slot-btn');
    const slotsGrid = document.getElementById('slots-grid');

    if (addSlotBtn && slotsGrid) {
        addSlotBtn.addEventListener('click', () => {
            const slotItem = document.createElement('div');
            slotItem.className = 'slot-item';
            slotItem.style.display = 'grid';
            slotItem.style.gridTemplateColumns = '1.2fr 1fr 1fr auto';
            slotItem.style.gap = '10px';
            slotItem.style.alignItems = 'center';
            slotItem.style.marginBottom = '10px';

            slotItem.innerHTML = `
                <select name="slotDays" required class="form-select">
                    <option value="Mon">Monday</option>
                    <option value="Tue">Tuesday</option>
                    <option value="Wed">Wednesday</option>
                    <option value="Thu">Thursday</option>
                    <option value="Fri">Friday</option>
                    <option value="Sat" selected>Saturday</option>
                    <option value="Sun">Sunday</option>
                </select>
                <input type="time" name="slotStarts" required class="form-control" value="09:00">
                <input type="time" name="slotEnds" required class="form-control" value="17:00">
                <button type="button" class="btn btn-secondary btn-sm remove-slot-btn" style="color:#dc2626;border-color:#fca5a5;">
                    &times; Remove
                </button>
            `;
            slotsGrid.appendChild(slotItem);

            slotItem.querySelector('.remove-slot-btn').addEventListener('click', () => {
                slotItem.remove();
            });
        });

        // Attach event listener to existing slot remove buttons
        document.querySelectorAll('.remove-slot-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const item = e.target.closest('.slot-item');
                if (item) item.remove();
            });
        });
    }

    // 4. Modal Handlers (delegated to top-level functions — see bottom of file)

    // Close modal when clicking outside modal-content
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
                overlay.style.display = 'none';
                document.body.style.overflow = '';
            }
        });
    });

    // 5. Auto-dismiss alerts after 6 seconds
    setTimeout(() => {
        document.querySelectorAll('.alert').forEach(alert => {
            alert.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
            alert.style.opacity = '0';
            alert.style.transform = 'translateY(-10px)';
            setTimeout(() => alert.remove(), 500);
        });
    }, 6000);

    // 6. Check for URL parameters like ?message=success (as on blooddonation.lk)
    const urlParams = new URLSearchParams(window.location.search);
    const msg = urlParams.get('message');
    if (msg === 'success') {
        showPopup("Operation completed successfully!");
    }

    // 7. Modern reveal-on-scroll
    const revealEls = document.querySelectorAll('.stat-card, .request-card, .donor-card, .eligibility-card, .finder-card, .matrix-card, .form-card');
    revealEls.forEach(el => el.classList.add('reveal'));
    const io = new IntersectionObserver((entries) => {
        entries.forEach(e => { if (e.isIntersecting) { e.target.classList.add('visible'); io.unobserve(e.target); } });
    }, { threshold: 0.12 });
    revealEls.forEach(el => io.observe(el));

    // 8. Animated counters for .stat-number (numeric only)
    const counters = document.querySelectorAll('.stat-number');
    const cio = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            const el = entry.target;
            const raw = (el.textContent || '').trim().replace(/[^0-9]/g, '');
            if (!raw) return;
            const target = parseInt(raw, 10);
            if (isNaN(target)) return;
            const dur = 1200; const t0 = performance.now();
            function tick(t) {
                const p = Math.min(1, (t - t0) / dur);
                el.textContent = Math.round(target * (1 - Math.pow(1 - p, 3)));
                if (p < 1) requestAnimationFrame(tick);
            }
            requestAnimationFrame(tick);
            cio.unobserve(el);
        });
    }, { threshold: 0.4 });
    counters.forEach(el => cio.observe(el));

    // 9. Auto-scroll long tables: any data table with 12+ body rows gets
    // a capped scroll box with sticky header (project-wide rule)
    document.querySelectorAll('.table-responsive').forEach(wrap => {
        const table = wrap.querySelector('table.data-table');
        if (!table) return;
        const rows = table.querySelectorAll('tbody tr').length;
        if (rows > 12) wrap.classList.add('table-scroll');
    });
});

// Top-level modal helpers: available to inline onclick handlers immediately,
// even before DOMContentLoaded fires or if deferred script timing shifts.
function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'flex';
        document.body.style.overflow = 'hidden';
    }
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'none';
        document.body.style.overflow = '';
    }
}
