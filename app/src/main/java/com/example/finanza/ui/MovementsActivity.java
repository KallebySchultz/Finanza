package com.example.finanza.ui;

import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import com.example.finanza.R;
import com.google.android.material.textfield.TextInputEditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.example.finanza.db.AppDatabase;
import com.example.finanza.model.Lancamento;
import com.example.finanza.model.Usuario;
import com.example.finanza.model.Conta;
import com.example.finanza.model.Categoria;
import com.example.finanza.ui.AccountsActivity;
import com.example.finanza.MainActivity;
import com.example.finanza.ui.MenuActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Activity para gerenciamento de movimentações (transações)
 */
public class MovementsActivity extends AppCompatActivity {

    private AppDatabase db;
    private int usuarioIdAtual;

    // Para navegação de mês/ano
    private Calendar currentMonth;

    private TextView txtMonth, saldoMes;
    private ImageView btnPrevMonth, btnNextMonth;
    private LinearLayout transactionsList;

    // Painel customizado
    private FrameLayout addPanel, addTransactionPanel;
    private LinearLayout btnReceita, btnDespesa;
    private ImageButton navAdd;
    private Button btnSalvarLancamento;
    private TextInputEditText inputNome, inputConta, inputData, inputCategoria, inputValor;

    // Para lançamento
    private Conta contaSelecionada;
    private Categoria categoriaSelecionada;
    private long dataSelecionada = System.currentTimeMillis();
    private boolean isReceitaPanel = true; // true: receita, false: despesa

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movements);

        db = Room.databaseBuilder(getApplicationContext(),
                        AppDatabase.class, "finanza-db")
                .allowMainThreadQueries()
                .build();

        // Busca usuário atual
        List<Usuario> usuarios = db.usuarioDao().listarTodos();
        usuarioIdAtual = usuarios.size() > 0 ? usuarios.get(0).id : 0;

        // Referências dos elementos
        txtMonth = findViewById(R.id.txt_month);
        saldoMes = findViewById(R.id.saldo_mes);
        btnPrevMonth = findViewById(R.id.btn_prev_month);
        btnNextMonth = findViewById(R.id.btn_next_month);
        transactionsList = findViewById(R.id.transactions_list);

        // Painel customizado
        navAdd = findViewById(R.id.nav_add);
        addPanel = findViewById(R.id.add_panel);
        btnReceita = findViewById(R.id.btnReceita);
        btnDespesa = findViewById(R.id.btnDespesa);
        addTransactionPanel = findViewById(R.id.add_transaction_panel);
        btnSalvarLancamento = findViewById(R.id.btn_salvar_lancamento);
        inputNome = findViewById(R.id.input_nome);
        inputConta = findViewById(R.id.input_conta);
        inputData = findViewById(R.id.input_data);
        inputCategoria = findViewById(R.id.input_categoria);
        inputValor = findViewById(R.id.input_valor);

        // Inicializa mês atual (corrige para garantir hora zero)
        currentMonth = Calendar.getInstance();
        currentMonth.set(Calendar.DAY_OF_MONTH, 1);
        currentMonth.set(Calendar.HOUR_OF_DAY, 0);
        currentMonth.set(Calendar.MINUTE, 0);
        currentMonth.set(Calendar.SECOND, 0);
        currentMonth.set(Calendar.MILLISECOND, 0);

        // Navegação dos meses
        btnPrevMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            currentMonth.set(Calendar.DAY_OF_MONTH, 1);
            currentMonth.set(Calendar.HOUR_OF_DAY, 0);
            currentMonth.set(Calendar.MINUTE, 0);
            currentMonth.set(Calendar.SECOND, 0);
            currentMonth.set(Calendar.MILLISECOND, 0);
            updateMovements();
        });
        btnNextMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            currentMonth.set(Calendar.DAY_OF_MONTH, 1);
            currentMonth.set(Calendar.HOUR_OF_DAY, 0);
            currentMonth.set(Calendar.MINUTE, 0);
            currentMonth.set(Calendar.SECOND, 0);
            currentMonth.set(Calendar.MILLISECOND, 0);
            updateMovements();
        });

        // Navegação por clique no mês (abre DatePicker apenas para mês/ano)
        txtMonth.setOnClickListener(v -> {
            int year = currentMonth.get(Calendar.YEAR);
            int month = currentMonth.get(Calendar.MONTH);

            DatePickerDialog dpd = new DatePickerDialog(this, (view, y, m, d) -> {
                currentMonth.set(Calendar.YEAR, y);
                currentMonth.set(Calendar.MONTH, m);
                currentMonth.set(Calendar.DAY_OF_MONTH, 1);
                currentMonth.set(Calendar.HOUR_OF_DAY, 0);
                currentMonth.set(Calendar.MINUTE, 0);
                currentMonth.set(Calendar.SECOND, 0);
                currentMonth.set(Calendar.MILLISECOND, 0);
                updateMovements();
            }, year, month, 1);

            // Esconde o campo dia do DatePicker
            try {
                int daySpinnerId = getResources().getIdentifier("android:id/day", null, null);
                if (daySpinnerId != 0) {
                    View daySpinner = dpd.getDatePicker().findViewById(daySpinnerId);
                    if (daySpinner != null) daySpinner.setVisibility(View.GONE);
                } else {
                    java.lang.reflect.Field[] datePickerFields = dpd.getDatePicker().getClass().getDeclaredFields();
                    for (java.lang.reflect.Field datePickerField : datePickerFields) {
                        if ("mDaySpinner".equals(datePickerField.getName()) || "mDayPicker".equals(datePickerField.getName())) {
                            datePickerField.setAccessible(true);
                            Object dayPicker = datePickerField.get(dpd.getDatePicker());
                            ((View) dayPicker).setVisibility(View.GONE);
                        }
                    }
                }
            } catch (Exception ignored) {}

            dpd.show();
        });

        // Long click on month for search functionality
        txtMonth.setOnLongClickListener(v -> {
            showSearchDialog();
            return true;
        });

        // Navegação pelos botões inferiores
        ImageView navHome = findViewById(R.id.nav_home);
        ImageView navMovements = findViewById(R.id.nav_movements);
        ImageView navAccounts = findViewById(R.id.nav_accounts);
        ImageView navMenu = findViewById(R.id.nav_menu);

        navHome.setOnClickListener(v -> {
            Intent intent = new Intent(MovementsActivity.this, MainActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });
        navMovements.setOnClickListener(v -> {/* já está na tela */});
        navAccounts.setOnClickListener(v -> {
            Intent intent = new Intent(MovementsActivity.this, AccountsActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });
        navMenu.setOnClickListener(v -> {
            // Smart back navigation - nav_menu acts as back button
            finish(); // Returns to previous activity
        });

        navMovements.setColorFilter(getResources().getColor(R.color.accentBlue));
        navHome.setColorFilter(getResources().getColor(R.color.white));
        navAccounts.setColorFilter(getResources().getColor(R.color.white));
        navMenu.setColorFilter(getResources().getColor(R.color.white));

        // Botão adicionar (painel customizado igual MainActivity)
        navAdd.setOnClickListener(v -> {
            if (addPanel.getVisibility() == View.GONE && addTransactionPanel.getVisibility() == View.GONE) {
                addPanel.setVisibility(View.VISIBLE);
                navAdd.setImageResource(R.drawable.ic_close);
            } else {
                addPanel.setVisibility(View.GONE);
                addTransactionPanel.setVisibility(View.GONE);
                navAdd.setImageResource(R.drawable.ic_add);
            }
        });

        addPanel.setOnClickListener(v -> {
            addPanel.setVisibility(View.GONE);
            navAdd.setImageResource(R.drawable.ic_add);
        });

        btnReceita.setOnClickListener(v -> {
            addPanel.setVisibility(View.GONE);
            addTransactionPanel.setVisibility(View.VISIBLE);
            navAdd.setImageResource(R.drawable.ic_close);
            isReceitaPanel = true;
            inicializarCamposPainel(inputNome, inputConta, inputData, inputCategoria, inputValor, true);
        });

        btnDespesa.setOnClickListener(v -> {
            addPanel.setVisibility(View.GONE);
            addTransactionPanel.setVisibility(View.VISIBLE);
            navAdd.setImageResource(R.drawable.ic_close);
            isReceitaPanel = false;
            inicializarCamposPainel(inputNome, inputConta, inputData, inputCategoria, inputValor, false);
        });

        // Campo Conta (dialogo de seleção)
        inputConta.setOnClickListener(v -> {
            mostrarDialogoSelecaoContas(inputConta);
        });

        // Campo Data (DatePicker)
        inputData.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(dataSelecionada);
            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar chosen = Calendar.getInstance();
                        chosen.set(year, month, dayOfMonth);
                        dataSelecionada = chosen.getTimeInMillis();
                        inputData.setText(String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.show();
        });

        // Campo Categoria (dialogo de seleção)
        inputCategoria.setOnClickListener(v -> {
            mostrarDialogoSelecaoCategorias(inputCategoria, isReceitaPanel ? "receita" : "despesa");
        });

        btnSalvarLancamento.setOnClickListener(v -> {
            String nome = inputNome.getText() != null ? inputNome.getText().toString().trim() : "";
            String valorStr = inputValor.getText() != null ? inputValor.getText().toString().replace(",", ".").trim() : "";

            // Clear previous errors
            inputNome.setError(null);
            inputConta.setError(null);
            inputData.setError(null);
            inputCategoria.setError(null);
            inputValor.setError(null);

            boolean hasError = false;

            if (contaSelecionada == null) {
                inputConta.setError("Selecione a conta");
                hasError = true;
            }
            if (categoriaSelecionada == null) {
                inputCategoria.setError("Selecione a categoria");
                hasError = true;
            }
            if (valorStr.isEmpty()) {
                inputValor.setError("Digite o valor");
                hasError = true;
            }

            if (!hasError) {
                try {
                    double valor = Double.parseDouble(valorStr);

                    // Validate positive value
                    if (valor <= 0) {
                        inputValor.setError("O valor deve ser maior que zero");
                        return;
                    }

                    Lancamento lancamento = new Lancamento();
                    lancamento.valor = valor;
                    lancamento.data = dataSelecionada;
                    lancamento.descricao = nome.isEmpty() ? (isReceitaPanel ? "Receita" : "Despesa") : nome;
                    lancamento.contaId = contaSelecionada.id;
                    lancamento.categoriaId = categoriaSelecionada.id;
                    lancamento.usuarioId = usuarioIdAtual;
                    lancamento.tipo = isReceitaPanel ? "receita" : "despesa";
                    db.lancamentoDao().inserir(lancamento);
                    updateMovements();
                    addTransactionPanel.setVisibility(View.GONE);
                    navAdd.setImageResource(R.drawable.ic_add);
                    limparCamposPainel(inputNome, inputConta, inputData, inputCategoria, inputValor);
                    Toast.makeText(this, "Transação salva!", Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    inputValor.setError("Valor inválido! Use apenas números e ponto para decimais.");
                }
            }
        });

        updateMovements();
    }

    private void inicializarCamposPainel(TextInputEditText inputNome,
                                         TextInputEditText inputConta,
                                         TextInputEditText inputData,
                                         TextInputEditText inputCategoria,
                                         TextInputEditText inputValor,
                                         boolean isReceita) {
        inputNome.setText("");
        inputConta.setText(contaSelecionada != null ? contaSelecionada.nome : "");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dataSelecionada);
        inputData.setText(String.format("%02d/%02d/%04d", calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR)));
        inputCategoria.setText(categoriaSelecionada != null ? categoriaSelecionada.nome : "");
        inputValor.setText("");
    }

    private void limparCamposPainel(TextInputEditText inputNome,
                                    TextInputEditText inputConta,
                                    TextInputEditText inputData,
                                    TextInputEditText inputCategoria,
                                    TextInputEditText inputValor) {
        inputNome.setText("");
        inputConta.setText("");
        inputData.setText("");
        inputCategoria.setText("");
        inputValor.setText("");
        categoriaSelecionada = null;
        dataSelecionada = System.currentTimeMillis();
    }

    /**
     * Atualiza lista de movimentações do mês atual
     */
    private void updateMovements() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", new Locale("pt", "BR"));
        txtMonth.setText(monthFormat.format(currentMonth.getTime()).toUpperCase());

        transactionsList.removeAllViews();

        Calendar inicio = (Calendar) currentMonth.clone();
        inicio.set(Calendar.HOUR_OF_DAY, 0);
        inicio.set(Calendar.MINUTE, 0);
        inicio.set(Calendar.SECOND, 0);
        inicio.set(Calendar.MILLISECOND, 0);

        Calendar fim = (Calendar) currentMonth.clone();
        fim.set(Calendar.DAY_OF_MONTH, fim.getActualMaximum(Calendar.DAY_OF_MONTH));
        fim.set(Calendar.HOUR_OF_DAY, 23);
        fim.set(Calendar.MINUTE, 59);
        fim.set(Calendar.SECOND, 59);
        fim.set(Calendar.MILLISECOND, 999);

        List<Lancamento> lancamentos = db.lancamentoDao().listarPorUsuarioPeriodo(
                usuarioIdAtual,
                inicio.getTimeInMillis(),
                fim.getTimeInMillis());

        double saldoFinal = 0.0;

        if (lancamentos != null && lancamentos.size() > 0) {
            SimpleDateFormat diaFormat = new SimpleDateFormat("EEEE, d", new Locale("pt", "BR"));
            Collections.sort(lancamentos, (l1, l2) -> Long.compare(l2.data, l1.data));

            long ultimoDia = -1;
            for (Lancamento lanc : lancamentos) {
                Calendar dataLanc = Calendar.getInstance();
                dataLanc.setTimeInMillis(lanc.data);
                long diaMillis = dataLanc.get(Calendar.YEAR) * 1000 + dataLanc.get(Calendar.DAY_OF_YEAR);

                if (diaMillis != ultimoDia) {
                    TextView diaHeader = new TextView(this);
                    diaHeader.setText(diaFormat.format(dataLanc.getTime()).toUpperCase());
                    diaHeader.setTextColor(getResources().getColor(R.color.white));
                    diaHeader.setTextSize(15);
                    diaHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                    diaHeader.setPadding(0, 24, 0, 12);
                    transactionsList.addView(diaHeader);
                    ultimoDia = diaMillis;
                }

                LinearLayout transItem = new LinearLayout(this);
                transItem.setOrientation(LinearLayout.HORIZONTAL);
                transItem.setPadding(12, 16, 12, 16);
                transItem.setBackground(getResources().getDrawable(R.drawable.bg_transaction_item));
                LinearLayout.LayoutParams transItemParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                transItemParams.bottomMargin = 8;
                transItem.setLayoutParams(transItemParams);

                // Obtém nome da categoria
                Categoria categoria = db.categoriaDao().buscarPorId(lanc.categoriaId);
                String categoriaNome = categoria != null ? categoria.nome : "";

                TextView nome = new TextView(this);
                nome.setText(lanc.descricao + " • " + categoriaNome);
                nome.setTextColor(getResources().getColor(R.color.white));
                nome.setTextSize(17);
                nome.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView valor = new TextView(this);
                valor.setText(formatarMoeda(lanc.valor));
                valor.setTextColor(getResources().getColor(
                        lanc.tipo.equals("receita") ? R.color.positiveGreen : R.color.negativeRed));
                valor.setTextSize(17);
                valor.setTypeface(null, android.graphics.Typeface.BOLD);
                valor.setPadding(20, 0, 0, 0);

                transItem.addView(nome);
                transItem.addView(valor);

                final Lancamento finalLanc = lanc;
                transItem.setOnClickListener(v -> editarLancamento(finalLanc));
                transItem.setOnLongClickListener(v -> {
                    confirmarExclusaoLancamento(finalLanc);
                    return true;
                });

                transactionsList.addView(transItem);

                saldoFinal += lanc.tipo.equals("receita") ? lanc.valor : -lanc.valor;
            }
        }

        saldoMes.setText(formatarMoeda(saldoFinal));
    }

    /**
     * Modal para edição de movimentação (transação) centralizado na tela, com botões corrigidos
     */
    private void editarLancamento(Lancamento lancamento) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Transação");

        // FrameLayout centralizado
        FrameLayout frameLayout = new FrameLayout(this);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER // CENTRALIZA O MODAL NA TELA!
        );
        frameLayout.setLayoutParams(frameParams);

        // ScrollView para garantir responsividade
        ScrollView scrollView = new ScrollView(this);

        // LinearLayout principal do modal
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dpPadding = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        layout.setPadding(dpPadding, dpPadding, dpPadding, dpPadding);
        layout.setBackground(getResources().getDrawable(R.drawable.bg_modal_white));
        layout.setElevation(16f); // Add high elevation to ensure modal appears above everything
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 340, getResources().getDisplayMetrics()),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.setLayoutParams(layoutParams);

        // Título do modal
        TextView title = new TextView(this);
        title.setText("Editar Transação");
        title.setTextSize(22);
        title.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dpPadding / 2;
        title.setLayoutParams(titleParams);
        layout.addView(title);

        // Campo descrição
        final TextInputEditText inputDescricao = new TextInputEditText(this);
        inputDescricao.setHint("Descrição");
        inputDescricao.setText(lancamento.descricao);
        inputDescricao.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        inputDescricao.setHintTextColor(getResources().getColor(R.color.primaryDarkBlue));
        inputDescricao.setBackground(getResources().getDrawable(R.drawable.edittext_bg));
        LinearLayout.LayoutParams inputParams1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams1.bottomMargin = dpPadding / 2;
        inputDescricao.setLayoutParams(inputParams1);
        layout.addView(inputDescricao);

        // Campo valor
        final TextInputEditText inputValor = new TextInputEditText(this);
        inputValor.setHint("Valor");
        inputValor.setText(String.valueOf(lancamento.valor));
        inputValor.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        inputValor.setHintTextColor(getResources().getColor(R.color.primaryDarkBlue));
        inputValor.setBackground(getResources().getDrawable(R.drawable.edittext_bg));
        inputValor.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams inputParams2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams2.bottomMargin = dpPadding / 2;
        inputValor.setLayoutParams(inputParams2);
        layout.addView(inputValor);

        // Categoria selection
        final TextInputEditText inputCategoria = new TextInputEditText(this);
        inputCategoria.setHint("Categoria");
        inputCategoria.setFocusable(false);
        inputCategoria.setClickable(true);
        inputCategoria.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        inputCategoria.setHintTextColor(getResources().getColor(R.color.primaryDarkBlue));
        inputCategoria.setBackground(getResources().getDrawable(R.drawable.edittext_bg));
        LinearLayout.LayoutParams inputParams3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams3.bottomMargin = dpPadding / 2;
        inputCategoria.setLayoutParams(inputParams3);

        List<Categoria> categorias = db.categoriaDao().listarPorTipo(lancamento.tipo);
        Categoria categoriaSelecionada = null;
        for (Categoria cat : categorias) {
            if (cat.id == lancamento.categoriaId) {
                categoriaSelecionada = cat;
                inputCategoria.setText(cat.nome);
                break;
            }
        }
        final Categoria[] categoriaFinal = {categoriaSelecionada};

        inputCategoria.setOnClickListener(v -> {
            mostrarDialogoSelecaoCategoriasParaEdicao(inputCategoria, categorias, categoriaFinal);
        });
        layout.addView(inputCategoria);

        // Botões "Salvar" e "Cancelar" com layout corrigido
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);

        Button btnSalvar = new Button(this);
        btnSalvar.setText("Salvar");
        btnSalvar.setTextColor(getResources().getColor(R.color.white));
        btnSalvar.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSalvar.setBackground(getResources().getDrawable(R.drawable.button_blue));
        LinearLayout.LayoutParams btnSalvarParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); // WRAP_CONTENT e peso 1f
        btnSalvarParams.rightMargin = dpPadding / 4;
        btnSalvar.setLayoutParams(btnSalvarParams);

        Button btnCancelar = new Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        btnCancelar.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancelar.setBackground(getResources().getDrawable(R.drawable.button_gray));
        LinearLayout.LayoutParams btnCancelarParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); // WRAP_CONTENT e peso 1f
        btnCancelarParams.leftMargin = dpPadding / 4;
        btnCancelar.setLayoutParams(btnCancelarParams);

        buttonLayout.addView(btnSalvar);
        buttonLayout.addView(btnCancelar);
        layout.addView(buttonLayout);

        // Adiciona o layout ao ScrollView e ao FrameLayout
        scrollView.addView(layout);
        frameLayout.addView(scrollView);
        builder.setView(frameLayout);

        // Fundo transparente para mostrar os cantos arredondados do modal
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        // Force center the dialog window
        if (dialog.getWindow() != null) {
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        // Listener do botão Salvar
        btnSalvar.setOnClickListener(v -> {
            String novaDescricao = inputDescricao.getText() != null ? inputDescricao.getText().toString().trim() : "";
            String novoValorStr = inputValor.getText() != null ? inputValor.getText().toString().trim() : "";

            if (!novaDescricao.isEmpty() && !novoValorStr.isEmpty() && categoriaFinal[0] != null) {
                try {
                    double novoValor = Double.parseDouble(novoValorStr.replace(",", "."));
                    if (novoValor <= 0) {
                        Toast.makeText(this, "O valor deve ser maior que zero", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    lancamento.descricao = novaDescricao;
                    lancamento.valor = novoValor;
                    lancamento.categoriaId = categoriaFinal[0].id;
                    db.lancamentoDao().atualizar(lancamento);
                    updateMovements();
                    dialog.dismiss();
                    Toast.makeText(this, "Transação atualizada!", Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Valor inválido", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            }
        });

        // Listener do botão Cancelar
        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void confirmarExclusaoLancamento(Lancamento lancamento) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // FrameLayout para fundo arredondado e tamanho customizado
        FrameLayout frameLayout = new FrameLayout(this);

        // ScrollView para garantir responsividade
        ScrollView scrollView = new ScrollView(this);

        // LinearLayout principal do modal
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dpPadding = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        layout.setPadding(dpPadding, dpPadding, dpPadding, dpPadding);
        layout.setBackground(getResources().getDrawable(R.drawable.bg_modal_white));
        layout.setElevation(16f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 340, getResources().getDisplayMetrics()),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.setLayoutParams(layoutParams);

        // Título do modal
        TextView title = new TextView(this);
        title.setText("Excluir Transação");
        title.setTextSize(22);
        title.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dpPadding / 2;
        title.setLayoutParams(titleParams);
        layout.addView(title);

        // Mensagem de confirmação
        TextView messageText = new TextView(this);
        String message = "Deseja excluir a transação '" + lancamento.descricao +
                "' no valor de " + formatarMoeda(lancamento.valor) + "?";
        messageText.setText(message);
        messageText.setTextSize(16);
        messageText.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        messageText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.bottomMargin = dpPadding;
        messageText.setLayoutParams(messageParams);
        layout.addView(messageText);

        // Botões "Excluir" e "Cancelar" com layout consistente
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);

        Button btnExcluir = new Button(this);
        btnExcluir.setText("Sim, excluir");
        btnExcluir.setTextColor(getResources().getColor(R.color.white));
        btnExcluir.setTypeface(null, android.graphics.Typeface.BOLD);
        btnExcluir.setBackground(getResources().getDrawable(R.drawable.button_blue)); // Use blue for consistency
        LinearLayout.LayoutParams btnExcluirParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnExcluirParams.rightMargin = dpPadding / 4;
        btnExcluir.setLayoutParams(btnExcluirParams);

        Button btnCancelar = new Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        btnCancelar.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancelar.setBackground(getResources().getDrawable(R.drawable.button_gray));
        LinearLayout.LayoutParams btnCancelarParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnCancelarParams.leftMargin = dpPadding / 4;
        btnCancelar.setLayoutParams(btnCancelarParams);

        buttonLayout.addView(btnExcluir);
        buttonLayout.addView(btnCancelar);
        layout.addView(buttonLayout);

        // Adiciona o layout ao ScrollView e ao FrameLayout
        scrollView.addView(layout);
        frameLayout.addView(scrollView);
        builder.setView(frameLayout);

        // Fundo transparente para mostrar os cantos arredondados do modal
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        // Force center the dialog window
        if (dialog.getWindow() != null) {
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        // Listener do botão Excluir
        btnExcluir.setOnClickListener(v -> {
            db.lancamentoDao().deletar(lancamento);
            updateMovements();
            dialog.dismiss();
            Toast.makeText(this, "Transação excluída!", Toast.LENGTH_SHORT).show();
        });

        // Listener do botão Cancelar
        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // FrameLayout centralizado
        FrameLayout frameLayout = new FrameLayout(this);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER // CENTRALIZA O MODAL NA TELA!
        );
        frameLayout.setLayoutParams(frameParams);

        // ScrollView para garantir responsividade
        ScrollView scrollView = new ScrollView(this);

        // LinearLayout principal do modal
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dpPadding = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        layout.setPadding(dpPadding, dpPadding, dpPadding, dpPadding);
        layout.setBackground(getResources().getDrawable(R.drawable.bg_modal_white));
        layout.setElevation(16f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 340, getResources().getDisplayMetrics()),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.setLayoutParams(layoutParams);

        // Título do modal
        TextView title = new TextView(this);
        title.setText("🔍 Buscar Transações");
        title.setTextSize(22);
        title.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dpPadding / 2;
        title.setLayoutParams(titleParams);
        layout.addView(title);

        // Campo de busca
        final EditText inputBusca = new EditText(this);
        inputBusca.setHint("Digite a descrição ou valor...");
        inputBusca.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        inputBusca.setHintTextColor(getResources().getColor(R.color.darkGray));
        inputBusca.setBackground(getResources().getDrawable(R.drawable.edittext_bg));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.bottomMargin = dpPadding / 2;
        inputBusca.setLayoutParams(inputParams);
        layout.addView(inputBusca);

        // Botões "Buscar", "Ver Todas" e "Cancelar"
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.VERTICAL);
        buttonLayout.setGravity(Gravity.CENTER);

        Button btnBuscar = new Button(this);
        btnBuscar.setText("Buscar");
        btnBuscar.setTextColor(getResources().getColor(R.color.white));
        btnBuscar.setTypeface(null, android.graphics.Typeface.BOLD);
        btnBuscar.setBackground(getResources().getDrawable(R.drawable.button_blue));
        LinearLayout.LayoutParams btnBuscarParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnBuscarParams.bottomMargin = dpPadding / 4;
        btnBuscar.setLayoutParams(btnBuscarParams);

        Button btnVerTodas = new Button(this);
        btnVerTodas.setText("Ver Todas");
        btnVerTodas.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        btnVerTodas.setTypeface(null, android.graphics.Typeface.BOLD);
        btnVerTodas.setBackground(getResources().getDrawable(R.drawable.button_gray));
        LinearLayout.LayoutParams btnVerTodasParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnVerTodasParams.bottomMargin = dpPadding / 4;
        btnVerTodas.setLayoutParams(btnVerTodasParams);

        Button btnCancelar = new Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        btnCancelar.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancelar.setBackground(getResources().getDrawable(R.drawable.button_gray));
        LinearLayout.LayoutParams btnCancelarParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnCancelar.setLayoutParams(btnCancelarParams);

        buttonLayout.addView(btnBuscar);
        buttonLayout.addView(btnVerTodas);
        buttonLayout.addView(btnCancelar);
        layout.addView(buttonLayout);

        // Adiciona o layout ao ScrollView e ao FrameLayout
        scrollView.addView(layout);
        frameLayout.addView(scrollView);
        builder.setView(frameLayout);

        // Fundo transparente para mostrar os cantos arredondados do modal
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        // Force center the dialog window
        if (dialog.getWindow() != null) {
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        // Listener do botão Buscar
        btnBuscar.setOnClickListener(v -> {
            String termoBusca = inputBusca.getText() != null ? inputBusca.getText().toString().trim() : "";
            if (!termoBusca.isEmpty()) {
                buscarTransacoes(termoBusca);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Digite um termo para buscar", Toast.LENGTH_SHORT).show();
            }
        });

        // Listener do botão Ver Todas
        btnVerTodas.setOnClickListener(v -> {
            updateMovements();
            dialog.dismiss();
        });

        // Listener do botão Cancelar
        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void buscarTransacoes(String termoBusca) {
        txtMonth.setText("BUSCA: " + termoBusca.toUpperCase());

        transactionsList.removeAllViews();

        String searchPattern = "%" + termoBusca + "%";
        List<Lancamento> resultados = db.lancamentoDao().buscarPorDescricaoOuValor(usuarioIdAtual, searchPattern);

        double saldoTotal = 0.0;

        if (resultados != null && resultados.size() > 0) {
            SimpleDateFormat dataFormat = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR"));

            for (Lancamento lanc : resultados) {
                LinearLayout resultItem = new LinearLayout(this);
                resultItem.setOrientation(LinearLayout.HORIZONTAL);
                resultItem.setPadding(0, 12, 0, 12);

                Categoria categoria = db.categoriaDao().buscarPorId(lanc.categoriaId);

                TextView descricao = new TextView(this);
                descricao.setText(lanc.descricao + " • " + (categoria != null ? categoria.nome : "") + " • " + dataFormat.format(new java.util.Date(lanc.data)));
                descricao.setTextColor(getResources().getColor(R.color.white));
                descricao.setTextSize(15);
                descricao.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView valor = new TextView(this);
                valor.setText(formatarMoeda(lanc.valor));
                valor.setTextColor(getResources().getColor(
                        lanc.tipo.equals("receita") ? R.color.positiveGreen : R.color.negativeRed));
                valor.setTextSize(15);
                valor.setTypeface(null, android.graphics.Typeface.BOLD);

                resultItem.addView(descricao);
                resultItem.addView(valor);

                final Lancamento finalLanc = lanc;
                resultItem.setOnClickListener(v -> editarLancamento(finalLanc));
                resultItem.setOnLongClickListener(v -> {
                    confirmarExclusaoLancamento(finalLanc);
                    return true;
                });

                transactionsList.addView(resultItem);

                saldoTotal += lanc.tipo.equals("receita") ? lanc.valor : -lanc.valor;
            }
        } else {
            TextView noResults = new TextView(this);
            noResults.setText("Nenhuma transação encontrada para: " + termoBusca);
            noResults.setTextColor(getResources().getColor(R.color.white));
            noResults.setTextSize(16);
            noResults.setPadding(0, 20, 0, 20);
            noResults.setGravity(android.view.Gravity.CENTER);
            transactionsList.addView(noResults);
        }

        saldoMes.setText(String.format("%d resultados • %s",
                resultados != null ? resultados.size() : 0, formatarMoeda(saldoTotal)));
    }

    /**
     * Modal padronizado para seleção de contas
     */
    private void mostrarDialogoSelecaoContas(TextInputEditText inputConta) {
        List<Conta> contasList = db.contaDao().listarPorUsuario(usuarioIdAtual);
        
        if (contasList.isEmpty()) {
            Toast.makeText(this, "Nenhuma conta encontrada", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // FrameLayout centralizado
        FrameLayout frameLayout = new FrameLayout(this);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        frameLayout.setLayoutParams(frameParams);

        // ScrollView para garantir responsividade
        ScrollView scrollView = new ScrollView(this);

        // LinearLayout principal do modal
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dpPadding = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        layout.setPadding(dpPadding, dpPadding, dpPadding, dpPadding);
        layout.setBackground(getResources().getDrawable(R.drawable.bg_modal_white));
        layout.setElevation(16f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 340, getResources().getDisplayMetrics()),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.setLayoutParams(layoutParams);

        // Título do modal
        TextView title = new TextView(this);
        title.setText("Selecionar Conta");
        title.setTextSize(22);
        title.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dpPadding / 2;
        title.setLayoutParams(titleParams);
        layout.addView(title);

        // Lista de contas
        for (Conta conta : contasList) {
            Button btnConta = new Button(this);
            btnConta.setText(conta.nome);
            btnConta.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
            btnConta.setTypeface(null, android.graphics.Typeface.BOLD);
            btnConta.setBackground(getResources().getDrawable(R.drawable.button_gray));
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.bottomMargin = dpPadding / 4;
            btnConta.setLayoutParams(btnParams);
            layout.addView(btnConta);
            
            btnConta.setOnClickListener(v -> {
                contaSelecionada = conta;
                inputConta.setText(conta.nome);
                ((AlertDialog) v.getTag()).dismiss();
            });
        }

        // Botão Cancelar
        Button btnCancelar = new Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        btnCancelar.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancelar.setBackground(getResources().getDrawable(R.drawable.button_gray));
        LinearLayout.LayoutParams btnCancelarParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnCancelar.setLayoutParams(btnCancelarParams);
        layout.addView(btnCancelar);

        // Adiciona o layout ao ScrollView e ao FrameLayout
        scrollView.addView(layout);
        frameLayout.addView(scrollView);
        builder.setView(frameLayout);

        // Fundo transparente para mostrar os cantos arredondados do modal
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        // Set dialog reference for buttons
        for (int i = 0; i < layout.getChildCount() - 1; i++) {
            if (layout.getChildAt(i) instanceof Button) {
                layout.getChildAt(i).setTag(dialog);
            }
        }

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Modal padronizado para seleção de categorias
     */
    private void mostrarDialogoSelecaoCategorias(TextInputEditText inputCategoria, String tipo) {
        List<Categoria> categoriasList = db.categoriaDao().listarPorTipo(tipo);
        
        if (categoriasList.isEmpty()) {
            Toast.makeText(this, "Nenhuma categoria de " + tipo + " encontrada", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // FrameLayout centralizado
        FrameLayout frameLayout = new FrameLayout(this);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        frameLayout.setLayoutParams(frameParams);

        // ScrollView para garantir responsividade
        ScrollView scrollView = new ScrollView(this);

        // LinearLayout principal do modal
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dpPadding = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        layout.setPadding(dpPadding, dpPadding, dpPadding, dpPadding);
        layout.setBackground(getResources().getDrawable(R.drawable.bg_modal_white));
        layout.setElevation(16f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 340, getResources().getDisplayMetrics()),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.setLayoutParams(layoutParams);

        // Título do modal
        TextView title = new TextView(this);
        title.setText("Selecionar Categoria");
        title.setTextSize(22);
        title.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dpPadding / 2;
        title.setLayoutParams(titleParams);
        layout.addView(title);

        // Lista de categorias
        for (Categoria categoria : categoriasList) {
            Button btnCategoria = new Button(this);
            btnCategoria.setText(categoria.nome);
            btnCategoria.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
            btnCategoria.setTypeface(null, android.graphics.Typeface.BOLD);
            btnCategoria.setBackground(getResources().getDrawable(R.drawable.button_gray));
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.bottomMargin = dpPadding / 4;
            btnCategoria.setLayoutParams(btnParams);
            layout.addView(btnCategoria);
            
            btnCategoria.setOnClickListener(v -> {
                categoriaSelecionada = categoria;
                inputCategoria.setText(categoria.nome);
                ((AlertDialog) v.getTag()).dismiss();
            });
        }

        // Botão Cancelar
        Button btnCancelar = new Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        btnCancelar.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancelar.setBackground(getResources().getDrawable(R.drawable.button_gray));
        LinearLayout.LayoutParams btnCancelarParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnCancelar.setLayoutParams(btnCancelarParams);
        layout.addView(btnCancelar);

        // Adiciona o layout ao ScrollView e ao FrameLayout
        scrollView.addView(layout);
        frameLayout.addView(scrollView);
        builder.setView(frameLayout);

        // Fundo transparente para mostrar os cantos arredondados do modal
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        // Set dialog reference for buttons
        for (int i = 0; i < layout.getChildCount() - 1; i++) {
            if (layout.getChildAt(i) instanceof Button) {
                layout.getChildAt(i).setTag(dialog);
            }
        }

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Modal padronizado para seleção de categorias na edição
     */
    private void mostrarDialogoSelecaoCategoriasParaEdicao(EditText inputCategoria, List<Categoria> categorias, Categoria[] categoriaFinal) {
        if (categorias.isEmpty()) {
            Toast.makeText(this, "Nenhuma categoria encontrada", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // FrameLayout centralizado
        FrameLayout frameLayout = new FrameLayout(this);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        frameLayout.setLayoutParams(frameParams);

        // ScrollView para garantir responsividade
        ScrollView scrollView = new ScrollView(this);

        // LinearLayout principal do modal
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dpPadding = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        layout.setPadding(dpPadding, dpPadding, dpPadding, dpPadding);
        layout.setBackground(getResources().getDrawable(R.drawable.bg_modal_white));
        layout.setElevation(16f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 340, getResources().getDisplayMetrics()),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.setLayoutParams(layoutParams);

        // Título do modal
        TextView title = new TextView(this);
        title.setText("Selecionar Categoria");
        title.setTextSize(22);
        title.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dpPadding / 2;
        title.setLayoutParams(titleParams);
        layout.addView(title);

        // Lista de categorias
        for (Categoria categoria : categorias) {
            Button btnCategoria = new Button(this);
            btnCategoria.setText(categoria.nome);
            btnCategoria.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
            btnCategoria.setTypeface(null, android.graphics.Typeface.BOLD);
            btnCategoria.setBackground(getResources().getDrawable(R.drawable.button_gray));
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.bottomMargin = dpPadding / 4;
            btnCategoria.setLayoutParams(btnParams);
            layout.addView(btnCategoria);
            
            btnCategoria.setOnClickListener(v -> {
                categoriaFinal[0] = categoria;
                inputCategoria.setText(categoria.nome);
                ((AlertDialog) v.getTag()).dismiss();
            });
        }

        // Botão Cancelar
        Button btnCancelar = new Button(this);
        btnCancelar.setText("Cancelar");
        btnCancelar.setTextColor(getResources().getColor(R.color.primaryDarkBlue));
        btnCancelar.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancelar.setBackground(getResources().getDrawable(R.drawable.button_gray));
        LinearLayout.LayoutParams btnCancelarParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnCancelar.setLayoutParams(btnCancelarParams);
        layout.addView(btnCancelar);

        // Adiciona o layout ao ScrollView e ao FrameLayout
        scrollView.addView(layout);
        frameLayout.addView(scrollView);
        builder.setView(frameLayout);

        // Fundo transparente para mostrar os cantos arredondados do modal
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        // Set dialog reference for buttons
        for (int i = 0; i < layout.getChildCount() - 1; i++) {
            if (layout.getChildAt(i) instanceof Button) {
                layout.getChildAt(i).setTag(dialog);
            }
        }

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Formata valor monetário para exibição
     */
    private String formatarMoeda(double valor) {
        java.text.NumberFormat formatter = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("pt", "BR"));
        return formatter.format(valor);
    }
}