package de.rki.coronawarnapp.ui.main.home

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.fragment.app.Fragment
import de.rki.coronawarnapp.R
import de.rki.coronawarnapp.contactdiary.ui.ContactDiaryActivity
import de.rki.coronawarnapp.databinding.FragmentHomeBinding
import de.rki.coronawarnapp.util.DeviceUIState
import de.rki.coronawarnapp.util.DialogHelper
import de.rki.coronawarnapp.util.ExternalActionHelper
import de.rki.coronawarnapp.util.NetworkRequestWrapper
import de.rki.coronawarnapp.util.di.AutoInject
import de.rki.coronawarnapp.util.errors.RecoveryByResetDialogFactory
import de.rki.coronawarnapp.util.ui.doNavigate
import de.rki.coronawarnapp.util.ui.observe2
import de.rki.coronawarnapp.util.ui.viewBindingLazy
import de.rki.coronawarnapp.util.viewmodel.CWAViewModelFactoryProvider
import de.rki.coronawarnapp.util.viewmodel.cwaViewModels
import javax.inject.Inject

/**
 * After the user has finished the onboarding this fragment will be the heart of the application.
 * Three ViewModels are needed that this fragment shows all relevant information to the user.
 * Also the Menu is set here.
 */
class HomeFragment : Fragment(R.layout.fragment_home), AutoInject {

    @Inject lateinit var viewModelFactory: CWAViewModelFactoryProvider.Factory
    private val vm: HomeFragmentViewModel by cwaViewModels(
        ownerProducer = { requireActivity().viewModelStore },
        factoryProducer = { viewModelFactory }
    )

    val binding: FragmentHomeBinding by viewBindingLazy()

    @Inject lateinit var homeMenu: HomeMenu
    @Inject lateinit var tracingExplanationDialog: TracingExplanationDialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.tracingHeaderState.observe2(this) {
            binding.tracingHeader = it
        }
        vm.tracingCardState.observe2(this) {
            binding.tracingCard = it
        }
        vm.submissionCardState.observe2(this) {
            binding.submissionCard = it

            setupTestResultCard(it.deviceUiState)
        }

        setupToolbar()
        setupRiskCard()
        setupDiaryCard()

        binding.mainTracing.setOnClickListener {
            doNavigate(HomeFragmentDirections.actionMainFragmentToSettingsTracingFragment())
        }

        binding.mainAbout.mainCard.apply {
            setOnClickListener {
                ExternalActionHelper.openUrl(this@HomeFragment, getString(R.string.main_about_link))
            }
            contentDescription = getString(R.string.hint_external_webpage)
        }

        vm.popupEvents.observe2(this) { event ->
            when (event) {
                HomeFragmentEvents.ShowInteropDeltaOnboarding -> {
                    doNavigate(
                        HomeFragmentDirections.actionMainFragmentToOnboardingDeltaInteroperabilityFragment()
                    )
                }
                is HomeFragmentEvents.ShowTracingExplanation -> {
                    tracingExplanationDialog.show(event.activeTracingDaysInRetentionPeriod) {
                        vm.tracingExplanationWasShown()
                    }
                }
                HomeFragmentEvents.ShowErrorResetDialog -> {
                    RecoveryByResetDialogFactory(this).showDialog(
                        detailsLink = R.string.errors_generic_text_catastrophic_error_encryption_failure,
                        onPositive = { vm.errorResetDialogDismissed() }
                    )
                }
                HomeFragmentEvents.ShowDeleteTestDialog -> {
                    showRemoveTestDialog()
                }
                HomeFragmentEvents.GoToContactDiary -> {
                    context?.let { ContactDiaryActivity.start(it) }
                }
            }
        }

        vm.showLoweredRiskLevelDialog.observe2(this) {
            if (it) {
                showRiskLevelLoweredDialog()
            }
        }

        vm.observeTestResultToSchedulePositiveTestResultReminder()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshRequiredData()
        binding.mainScrollview.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
    }

    private fun showRemoveTestDialog() {
        val removeTestDialog = DialogHelper.DialogInstance(
            requireActivity(),
            R.string.submission_test_result_dialog_remove_test_title,
            R.string.submission_test_result_dialog_remove_test_message,
            R.string.submission_test_result_dialog_remove_test_button_positive,
            R.string.submission_test_result_dialog_remove_test_button_negative,
            positiveButtonFunction = {
                vm.deregisterWarningAccepted()
            }
        )
        DialogHelper.showDialog(removeTestDialog).apply {
            getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(context.getColor(R.color.colorTextSemanticRed))
        }
    }

    private fun setupRiskCard() {
        binding.riskCard.setOnClickListener {
            doNavigate(HomeFragmentDirections.actionMainFragmentToRiskDetailsFragment())
        }
        binding.riskCardContent.apply {
            riskCardButtonUpdate.setOnClickListener {
                vm.refreshDiagnosisKeys()
            }
            riskCardButtonEnableTracing.setOnClickListener {
                doNavigate(HomeFragmentDirections.actionMainFragmentToSettingsTracingFragment())
            }
        }
    }

    private fun setupTestResultCard(deviceUiState: NetworkRequestWrapper<DeviceUIState, Throwable>) {
        binding.mainTestUnregistered.apply {
            val navDirection = HomeFragmentDirections.actionMainFragmentToSubmissionDispatcher()
            submissionStatusCardUnregistered.setOnClickListener { doNavigate(navDirection) }
            submissionStatusCardUnregisteredButton.setOnClickListener { doNavigate(navDirection) }
        }

        // Test is not positive (pending, negative, invalid)
        binding.mainTestResult.apply {
            val navDirection = if (deviceUiState is NetworkRequestWrapper.RequestSuccessful) {
                when (deviceUiState.data) {
                    DeviceUIState.PAIRED_NEGATIVE -> HomeFragmentDirections
                        .actionMainFragmentToSubmissionTestResultNegativeFragment()
                    DeviceUIState.PAIRED_ERROR,
                    DeviceUIState.PAIRED_REDEEMED -> HomeFragmentDirections
                        .actionMainFragmentToSubmissionTestResultInvalidFragment()
                    else -> HomeFragmentDirections
                        .actionMainFragmentToSubmissionTestResultPendingFragment()
                }
            } else {
                HomeFragmentDirections.actionMainFragmentToSubmissionTestResultPendingFragment()
            }

            submissionStatusCardContent.setOnClickListener { doNavigate(navDirection) }
            submissionStatusCardContentButton.setOnClickListener { doNavigate(navDirection) }
        }

        // Test is positive
        binding.mainTestPositive.apply {
            val navDirection = HomeFragmentDirections
                .actionMainFragmentToSubmissionResultPositiveOtherWarningNoConsentFragment()

            submissionStatusCardPositive.setOnClickListener { doNavigate(navDirection) }
            submissionStatusCardPositiveButton.setOnClickListener { doNavigate(navDirection) }
        }

        binding.mainTestFailed.apply {
            setOnClickListener {
                vm.removeTestPushed()
            }
        }
        binding.mainTestReady.apply {
            val navDirections = HomeFragmentDirections
                .actionMainFragmentToSubmissionTestResultAvailableFragment()

            submissionStatusCardReady.setOnClickListener { doNavigate(navDirections) }
            submissionStatusCardReadyButton.setOnClickListener { doNavigate(navDirections) }
        }
    }

    private fun setupToolbar() {
        binding.mainHeaderShare.buttonIcon.apply {
            contentDescription = getString(R.string.button_share)
            setOnClickListener {
                doNavigate(HomeFragmentDirections.actionMainFragmentToMainSharingFragment())
            }
        }

        binding.mainHeaderOptionsMenu.buttonIcon.apply {
            contentDescription = getString(R.string.button_menu)
            setOnClickListener { homeMenu.showMenuFor(it) }
        }
    }

    private fun setupDiaryCard() {
        binding.contactDiaryCard.apply {
            contactDiaryCardHomescreenButton.setOnClickListener { vm.moveToContactDiary() }
            contactDiaryHomescreenCard.setOnClickListener { vm.moveToContactDiary() }
        }
    }

    private fun showRiskLevelLoweredDialog() {
        val riskLevelLoweredDialog = DialogHelper.DialogInstance(
            context = requireActivity(),
            title = R.string.risk_lowered_dialog_headline,
            message = R.string.risk_lowered_dialog_body,
            positiveButton = R.string.risk_lowered_dialog_button_confirm,
            negativeButton = null,
            cancelable = false,
            positiveButtonFunction = { vm.userHasAcknowledgedTheLoweredRiskLevel() }
        )

        DialogHelper.showDialog(riskLevelLoweredDialog).apply {
            getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(context.getColor(R.color.colorTextTint))
        }
    }
}
