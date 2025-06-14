package com.example.electricitybillestimator.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.electricitybillestimator.Bill;
import com.example.electricitybillestimator.BillAdapter;
import com.example.electricitybillestimator.DBHelper;
import com.example.electricitybillestimator.DetailActivity;
import com.example.electricitybillestimator.R;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

public class HomeFragment extends Fragment {

    Spinner spinnerMonth;
    EditText editUnit;
    SeekBar seekRebate;
    TextView txtRebateValue;
    Button btnCalculate, btnDeleteAll;
    ListView listView;

    DBHelper dbHelper;
    ArrayList<Bill> billList;
    BillAdapter adapter;

    ArrayList<Bill> deletedBillsBackup; // for undoing delete all

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        spinnerMonth = view.findViewById(R.id.spinnerMonth);
        editUnit = view.findViewById(R.id.editUnit);
        seekRebate = view.findViewById(R.id.seekRebate);
        txtRebateValue = view.findViewById(R.id.txtRebateValue);
        btnCalculate = view.findViewById(R.id.btnCalculate);
        btnDeleteAll = view.findViewById(R.id.btnDeleteAll);
        listView = view.findViewById(R.id.listView);

        dbHelper = new DBHelper(getContext());
        billList = dbHelper.getAllBills();

        adapter = new BillAdapter(getContext(), billList);
        listView.setAdapter(adapter);

        Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.fade_in);
        listView.setLayoutAnimation(new LayoutAnimationController(animation));

        ArrayAdapter<CharSequence> monthAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.months, android.R.layout.simple_spinner_item);
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMonth.setAdapter(monthAdapter);

        seekRebate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtRebateValue.setText("Rebate: " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnCalculate.setOnClickListener(v -> {
            String month = spinnerMonth.getSelectedItem().toString();
            String unitStr = editUnit.getText().toString().trim();

            if (unitStr.isEmpty()) {
                Toast.makeText(getContext(), "Please enter unit used", Toast.LENGTH_SHORT).show();
                return;
            }

            int unit = Integer.parseInt(unitStr);
            double totalCharges = calculateCharges(unit);
            double rebatePercentage = seekRebate.getProgress();
            double rebate = totalCharges * (rebatePercentage / 100.0);
            double finalCost = totalCharges - rebate;

            new AlertDialog.Builder(getContext())
                    .setTitle("Save Calculation")
                    .setMessage("Do you want to save this bill for " + month + "?")
                    .setPositiveButton("Save", (dialog, which) -> {
                        if (dbHelper.isMonthExists(month)) {
                            Toast.makeText(getContext(), "A bill for this month already exists.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        Bill bill = new Bill(0, month, unit, totalCharges, rebate, finalCost);
                        dbHelper.insertBill(bill);

                        billList.clear();
                        billList.addAll(dbHelper.getAllBills());
                        adapter.notifyDataSetChanged();

                        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        View currentFocus = requireActivity().getCurrentFocus();
                        if (imm != null && currentFocus != null) {
                            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                        }

                        editUnit.setText("");
                        Toast.makeText(getContext(), "Calculation saved successfully!", Toast.LENGTH_SHORT).show();
                        listView.post(() -> listView.setSelection(adapter.getCount() - 1));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        listView.setOnItemLongClickListener((parent, view12, position, id) -> {
            Bill billToDelete = billList.get(position);

            new AlertDialog.Builder(getContext())
                    .setTitle("Delete Bill")
                    .setMessage("Are you sure you want to delete this bill?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        dbHelper.deleteBillById(billToDelete.getId());
                        billList.remove(position);
                        adapter.notifyDataSetChanged();

                        Snackbar.make(view12, "Bill deleted", Snackbar.LENGTH_LONG)
                                .setAction("Undo", v1 -> {
                                    dbHelper.insertBill(billToDelete);
                                    billList.clear();
                                    billList.addAll(dbHelper.getAllBills());
                                    adapter.notifyDataSetChanged();
                                })
                                .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        btnDeleteAll.setOnClickListener(v -> {
            if (billList.isEmpty()) {
                Toast.makeText(getContext(), "No bills to delete", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(getContext())
                    .setTitle("Delete All")
                    .setMessage("Are you sure you want to delete all bills?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        deletedBillsBackup = new ArrayList<>(billList); // backup before delete
                        dbHelper.deleteAllBills();
                        billList.clear();
                        adapter.notifyDataSetChanged();

                        Snackbar.make(listView, "All bills deleted", Snackbar.LENGTH_LONG)
                                .setAction("Undo", v1 -> {
                                    for (Bill bill : deletedBillsBackup) {
                                        dbHelper.insertBill(bill);
                                    }
                                    billList.clear();
                                    billList.addAll(dbHelper.getAllBills());
                                    adapter.notifyDataSetChanged();
                                })
                                .show();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        listView.setOnItemClickListener((parent, view1, position, id) -> {
            Bill selectedBill = billList.get(position);
            Intent intent = new Intent(getActivity(), DetailActivity.class);
            intent.putExtra("bill", selectedBill);
            startActivity(intent);
        });

        return view;
    }

    private double calculateCharges(int unit) {
        double total = 0;
        if (unit <= 200) total = unit * 0.218;
        else if (unit <= 300) total = (200 * 0.218) + ((unit - 200) * 0.334);
        else if (unit <= 600) total = (200 * 0.218) + (100 * 0.334) + ((unit - 300) * 0.516);
        else if (unit <= 900) total = (200 * 0.218) + (100 * 0.334) + (300 * 0.516) + ((unit - 600) * 0.546);
        else total = (200 * 0.218) + (100 * 0.334) + (300 * 0.516) + (300 * 0.546) + ((unit - 900) * 0.571);
        return total;
    }
}
